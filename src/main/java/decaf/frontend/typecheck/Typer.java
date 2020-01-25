package decaf.frontend.typecheck;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.driver.error.*;
import decaf.frontend.scope.*;
import decaf.frontend.symbol.*;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.type.*;
import decaf.lowlevel.log.IndentPrinter;
import decaf.printing.PrettyScope;

import java.util.*;

/**
 * The typer phase: type check abstract syntax tree and annotate nodes with inferred (and checked) types.
 */
public class Typer extends Phase<Tree.TopLevel, Tree.TopLevel> implements TypeLitVisited {

    public Typer(Config config) {
        super("typer", config);
    }

    @Override
    public Tree.TopLevel transform(Tree.TopLevel tree) {
        var ctx = new ScopeStack(tree.globalScope);
        tree.accept(this, ctx);
        return tree;
    }

    @Override
    public void onSucceed(Tree.TopLevel tree) {
        if (config.target.equals(Config.Target.PA2)) {
            var printer = new PrettyScope(new IndentPrinter(config.output));
            printer.pretty(tree.globalScope);
            printer.flush();
        }
    }

    @Override
    public void visitTopLevel(Tree.TopLevel program, ScopeStack ctx) {
        for (var clazz : program.classes) {
            clazz.accept(this, ctx);
        }
    }

    @Override
    public void visitClassDef(Tree.ClassDef clazz, ScopeStack ctx) {
        ctx.open(clazz.symbol.scope);
        for (var field : clazz.fields) {
            field.accept(this, ctx);
        }
        ctx.close();
    }

    @Override
    public void visitMethodDef(Tree.MethodDef method, ScopeStack ctx) {
        ctx.open(method.symbol.scope);
        if (!method.isAbstract()) {
            method.body.get().accept(this, ctx);
            if (!method.symbol.type.returnType.isVoidType() && !method.body.get().returns) {
                issue(new MissingReturnError(method.body.get().pos));
            }
        }
        ctx.close();
    }

    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */
    private int loopLevel = 0;

    @Override
    public void visitBlock(Tree.Block block, ScopeStack ctx) {
        ctx.open(block.scope);
        for (var stmt : block.stmts) {
            stmt.accept(this, ctx);
            block.updateReturnType(stmt);
        }
        ctx.close();
        block.returns = !block.stmts.isEmpty() && block.stmts.get(block.stmts.size() - 1).returns;
    }

    @Override
    public void visitAssign(Tree.Assign stmt, ScopeStack ctx) {
        stmt.lhs.accept(this, ctx);
        stmt.rhs.accept(this, ctx);
        var lt = stmt.lhs.type;
        var rt = stmt.rhs.type;

        if (lt.noError() && rt.noError() && !rt.subtypeOf(lt)) {
            issue(new IncompatBinOpError(stmt.pos, lt.toString(), "=", rt.toString()));
            // Shall we return?
        }
        if (lt.noError() && stmt.lhs instanceof Tree.VarSel) {
            var lvar = (Tree.VarSel) stmt.lhs;
            if (lvar.isMethod) {
                issue(new AssignMethodError(stmt.pos, lvar.name));
                return;
            }
            if (lambdaLevel > 0 && lvar.symbol != null && lvar.symbol.domain() != ctx.currentScope() &&
                    !lvar.symbol.isMemberVar()) {
                Scope currentLambdaScope = ctx.currentScope();
                while (lvar.symbol.domain() != currentLambdaScope && currentLambdaScope.isLocalScope()) {
                    currentLambdaScope = ((LocalScope) currentLambdaScope).parent;
                }
                if (lvar.symbol.domain() != currentLambdaScope) {
                    issue(new AssignCaptureError(stmt.pos));
                }
            }
        }
    }

    @Override
    public void visitExprEval(Tree.ExprEval stmt, ScopeStack ctx) {
        stmt.expr.accept(this, ctx);
    }


    @Override
    public void visitIf(Tree.If stmt, ScopeStack ctx) {
        checkTestExpr(stmt.cond, ctx);
        stmt.trueBranch.accept(this, ctx);
        stmt.updateReturnType(stmt.trueBranch);
        if (stmt.falseBranch.isPresent()) {
            stmt.falseBranch.get().accept(this, ctx);
            stmt.updateReturnType(stmt.falseBranch.get());
        }
        // if-stmt returns a value iff both branches return
        stmt.returns = stmt.trueBranch.returns && stmt.falseBranch.isPresent() && stmt.falseBranch.get().returns;
    }

    @Override
    public void visitWhile(Tree.While loop, ScopeStack ctx) {
        checkTestExpr(loop.cond, ctx);
        loopLevel++;
        loop.body.accept(this, ctx);
        loop.updateReturnType(loop.body);
        loopLevel--;
    }

    @Override
    public void visitFor(Tree.For loop, ScopeStack ctx) {
        ctx.open(loop.scope);
        loop.init.accept(this, ctx);
        checkTestExpr(loop.cond, ctx);
        loop.update.accept(this, ctx);
        loopLevel++;
        for (var stmt : loop.body.stmts) {
            stmt.accept(this, ctx);
            loop.updateReturnType(stmt);
        }
        loopLevel--;
        ctx.close();
    }

    @Override
    public void visitBreak(Tree.Break stmt, ScopeStack ctx) {
        if (loopLevel == 0) {
            issue(new BreakOutOfLoopError(stmt.pos));
        }
    }

    /**
     * To know if we need to check the return type, we need to know if we are in a lambda expression, i.e.
     * lambdaLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a lambda expression, and decrease it when leaving one.
     */
    private int lambdaLevel = 0;

    @Override
    public void visitReturn(Tree.Return stmt, ScopeStack ctx) {
        stmt.expr.ifPresent(e -> e.accept(this, ctx));
        var actual = stmt.expr.map(e -> e.type).orElse(BuiltInType.VOID);
        if (lambdaLevel == 0) {
            var expected = ctx.currentMethod().type.returnType;
            if (actual.noError() && !actual.subtypeOf(expected)) {
                issue(new BadReturnTypeError(stmt.pos, expected.toString(), actual.toString()));
            }
        }
        stmt.returns = stmt.expr.isPresent();
        stmt.returnType = actual;
    }

    @Override
    public void visitPrint(Tree.Print stmt, ScopeStack ctx) {
        int i = 0;
        for (var expr : stmt.exprs) {
            expr.accept(this, ctx);
            i++;
            if (expr.type.noError() && !expr.type.isBaseType()) {
                issue(new BadPrintArgError(expr.pos, Integer.toString(i), expr.type.toString()));
            }
        }
    }

    private void checkTestExpr(Tree.Expr expr, ScopeStack ctx) {
        expr.accept(this, ctx);
        if (expr.type.noError() && !expr.type.eq(BuiltInType.BOOL)) {
            issue(new BadTestExpr(expr.pos));
        }
    }

    // Expressions

    @Override
    public void visitIntLit(Tree.IntLit that, ScopeStack ctx) {
        that.type = BuiltInType.INT;
    }

    @Override
    public void visitBoolLit(Tree.BoolLit that, ScopeStack ctx) {
        that.type = BuiltInType.BOOL;
    }

    @Override
    public void visitStringLit(Tree.StringLit that, ScopeStack ctx) {
        that.type = BuiltInType.STRING;
    }

    @Override
    public void visitNullLit(Tree.NullLit that, ScopeStack ctx) {
        that.type = BuiltInType.NULL;
    }

    @Override
    public void visitReadInt(Tree.ReadInt readInt, ScopeStack ctx) {
        readInt.type = BuiltInType.INT;
    }

    @Override
    public void visitReadLine(Tree.ReadLine readStringExpr, ScopeStack ctx) {
        readStringExpr.type = BuiltInType.STRING;
    }

    @Override
    public void visitUnary(Tree.Unary expr, ScopeStack ctx) {
        expr.operand.accept(this, ctx);
        var t = expr.operand.type;
        if (t.noError() && !compatible(expr.op, t)) {
            // Only report this error when the operand has no error, to avoid nested errors flushing.
            issue(new IncompatUnOpError(expr.pos, Tree.opStr(expr.op), t.toString()));
        }

        // Even when it doesn't type check, we could make a fair guess based on the operator kind.
        // Let's say the operator is `-`, then one possibly wants an integer as the operand.
        // Once he/she fixes the operand, according to our type inference rule, the whole unary expression
        // must have type int! Thus, we simply _assume_ it has type int, rather than `NoType`.
        expr.type = resultTypeOf(expr.op);
    }

    public boolean compatible(Tree.UnaryOp op, Type operand) {
        return switch (op) {
            case NEG -> operand.eq(BuiltInType.INT); // if e : int, then -e : int
            case NOT -> operand.eq(BuiltInType.BOOL); // if e : bool, then !e : bool
        };
    }

    public Type resultTypeOf(Tree.UnaryOp op) {
        return switch (op) {
            case NEG -> BuiltInType.INT;
            case NOT -> BuiltInType.BOOL;
        };
    }

    @Override
    public void visitBinary(Tree.Binary expr, ScopeStack ctx) {
        expr.lhs.accept(this, ctx);
        expr.rhs.accept(this, ctx);
        var t1 = expr.lhs.type;
        var t2 = expr.rhs.type;
        if (t1.noError() && t2.noError() && !compatible(expr.op, t1, t2)) {
            issue(new IncompatBinOpError(expr.pos, t1.toString(), Tree.opStr(expr.op), t2.toString()));
        }
        expr.type = resultTypeOf(expr.op);
    }

    public boolean compatible(Tree.BinaryOp op, Type lhs, Type rhs) {
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            // if e1, e2 : int, then e1 + e2 : int
            return lhs.eq(BuiltInType.INT) && rhs.eq(BuiltInType.INT);
        }

        if (op.equals(Tree.BinaryOp.AND) || op.equals(Tree.BinaryOp.OR)) { // logic
            // if e1, e2 : bool, then e1 && e2 : bool
            return lhs.eq(BuiltInType.BOOL) && rhs.eq(BuiltInType.BOOL);
        }

        if (op.equals(Tree.BinaryOp.EQ) || op.equals(Tree.BinaryOp.NE)) { // eq
            // if e1 : T1, e2 : T2, T1 <: T2 or T2 <: T1, then e1 == e2 : bool
            return lhs.subtypeOf(rhs) || rhs.subtypeOf(lhs);
        }

        // compare
        // if e1, e2 : int, then e1 > e2 : bool
        return lhs.eq(BuiltInType.INT) && rhs.eq(BuiltInType.INT);
    }

    public Type resultTypeOf(Tree.BinaryOp op) {
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            return BuiltInType.INT;
        }
        return BuiltInType.BOOL;
    }

    @Override
    public void visitNewArray(Tree.NewArray expr, ScopeStack ctx) {
        expr.elemType.accept(this, ctx);
        expr.length.accept(this, ctx);
        var et = expr.elemType.type;
        var lt = expr.length.type;

        if (et.isVoidType()) {
            issue(new BadArrElementError(expr.elemType.pos));
            expr.type = BuiltInType.ERROR;
        } else {
            expr.type = new ArrayType(et);
        }

        if (lt.noError() && !lt.eq(BuiltInType.INT)) {
            issue(new BadNewArrayLength(expr.length.pos));
        }
    }

    @Override
    public void visitNewClass(Tree.NewClass expr, ScopeStack ctx) {
        var clazz = ctx.lookupClass(expr.clazz.name);
        if (clazz.isPresent()) {
            if (clazz.get().isAbstract()) {
                issue(new InstantAbstractClassError(expr.pos, expr.clazz.name));
                expr.type = BuiltInType.ERROR;
            } else {
                expr.symbol = clazz.get();
                expr.type = expr.symbol.type;
            }
        } else {
            issue(new ClassNotFoundError(expr.pos, expr.clazz.name));
            expr.type = BuiltInType.ERROR;
        }
    }

    @Override
    public void visitThis(Tree.This expr, ScopeStack ctx) {
        if (ctx.currentMethod().isStatic()) {
            issue(new ThisInStaticFuncError(expr.pos));
        }
        expr.type = ctx.currentClass().type;
    }

    private boolean allowClassNameVar = false;

    @Override
    public void visitVarSel(Tree.VarSel expr, ScopeStack ctx) {
        expr.receiverClassName = Optional.empty();
        if (expr.receiver.isEmpty()) { // this class
            // Variable, which should be complicated since a legal variable could refer to a local var,
            // a visible member var, a class name, or a method name.
            var symbol = ctx.lookupBefore(expr.name, localVarDefPos.orElse(expr.pos));
            if (symbol.isPresent()) {
                if (symbol.get().isVarSymbol()) {
                    var var = (VarSymbol) symbol.get();
                    expr.symbol = var;
                    expr.type = var.type;
                    var capturedVar = var;
                    if (var.isMemberVar()) {
                        if (ctx.currentMethod().isStatic()) {
                            issue(new RefNonStaticError(expr.pos, ctx.currentMethod().name, expr.name));
                        } else {
                            expr.setThis();
                            capturedVar = (VarSymbol) ctx.currentMethod().scope.find("this").get();
                        }
                    }
                    if (lambdaLevel > 0 && capturedVar.domain() != ctx.currentScope()) {
                        Scope currentLambdaScope = ctx.currentScope();
                        while (capturedVar.domain() != currentLambdaScope) {
                            if (currentLambdaScope.isLocalScope()) {
                                currentLambdaScope = ((LocalScope) currentLambdaScope).parent;
                            } else if (currentLambdaScope.isLambdaScope()) {
                                // Capture the variable.
                                ((LambdaScope) currentLambdaScope).capture(capturedVar);
                                currentLambdaScope = ((LambdaScope) currentLambdaScope).parent;
                            } else {
                                // Formal or class scope, no lambda expressions outside.
                                break;
                            }
                        }
                    }
                    return;
                }

                if (symbol.get().isClassSymbol() && allowClassNameVar) { // special case: a class name
                    var clazz = (ClassSymbol) symbol.get();
                    expr.type = clazz.type;
                    expr.isClassName = true;
                    return;
                }

                if (symbol.get().isMethodSymbol()) {
                    var method = (MethodSymbol) symbol.get();
                    expr.type = method.type;
                    expr.isMethod = true;
                    expr.isStaticMethod = method.isStatic();
                    if (!ctx.currentMethod().isStatic()) {
                        expr.setThis();
                    }
                    expr.receiverClassName = Optional.of(ctx.currentClass().name);
                    if (!method.isStatic() && ctx.currentMethod().isStatic()) {
                        issue(new RefNonStaticError(expr.pos, ctx.currentMethod().name, expr.name));
                    }
                    return;
                }

                if (symbol.get().isLambdaSymbol()) {
                    // Impossible?
                    var lambda = (LambdaSymbol) symbol.get();
                    expr.type = lambda.type;
                    return;
                }
            }

            expr.type = BuiltInType.ERROR;
            issue(new UndeclVarError(expr.pos, expr.name));
            return;
        }

        // has receiver
        var receiver = expr.receiver.get();
        allowClassNameVar = true;
        receiver.accept(this, ctx);
        allowClassNameVar = false;
        var rt = receiver.type;
        expr.type = BuiltInType.ERROR;

        if (receiver instanceof Tree.VarSel) {
            var v1 = (Tree.VarSel) receiver;
            var v2 = expr.variable;
            if (v1.isClassName) {
                // Special case: invoking a static method, like MyClass.foo()
                var clazz = ctx.getClass(v1.name);
                var symbol = clazz.scope.lookup(v2.name);
                if (symbol.isPresent() && symbol.get().isMethodSymbol()) {
                    if (!((MethodSymbol) symbol.get()).isStatic()) {
                        // Cannot access a non-static method by MyClass.foo()
                        issue(new NotClassFieldError(expr.pos, expr.name, ctx.getClass(v1.name).type.toString()));
                        return;
                    }
                    expr.type = symbol.get().type;
                    expr.receiverClassName = Optional.of(v1.name);
                    expr.isMethod = true;
                    expr.isStaticMethod = true;
                } else if (symbol.isEmpty()) {
                    issue(new FieldNotFoundError(expr.pos, expr.name, ctx.getClass(v1.name).type.toString()));
                } else {
                    // Special case like MyClass.foo: report error cannot access field 'foo' from 'class : MyClass'
                    issue(new NotClassFieldError(expr.pos, expr.name, ctx.getClass(v1.name).type.toString()));
                }
                return;
            }
        }

        if (!rt.noError()) {
            return;
        }

        if (rt.isArrayType() && expr.variable.name.equals("length")) { // Special case: array.length()
            expr.isArrayLength = true;
            expr.type = new FunType(BuiltInType.INT, new ArrayList<>());
            return;
        }

        if (!rt.isClassType()) {
            issue(new NotClassFieldError(expr.pos, expr.name, rt.toString()));
            return;
        }

        expr.receiverClassName = Optional.of(((ClassType) rt).name);

        var ct = (ClassType) rt;
        var field = ctx.getClass(ct.name).scope.lookup(expr.name);
        if (field.isPresent() && field.get().isVarSymbol()) {
            var var = (VarSymbol) field.get();
            if (var.isMemberVar()) {
                expr.symbol = var;
                expr.type = var.type;
                if (!ctx.currentClass().type.subtypeOf(var.getOwner().type)) {
                    // member vars are protected
                    issue(new FieldNotAccessError(expr.pos, expr.name, ct.toString()));
                }
            }
        } else if (field.isPresent() && field.get().isMethodSymbol()) {
            expr.type = ((MethodSymbol) field.get()).type;
            expr.isMethod = true;
            expr.isStaticMethod = ((MethodSymbol) field.get()).isStatic();
        } else if (field.isEmpty()) {
            issue(new FieldNotFoundError(expr.pos, expr.name, ct.toString()));
        } else {
            issue(new NotClassFieldError(expr.pos, expr.name, ct.toString()));
        }
    }

    @Override
    public void visitIndexSel(Tree.IndexSel expr, ScopeStack ctx) {
        expr.array.accept(this, ctx);
        expr.index.accept(this, ctx);
        var at = expr.array.type;
        var it = expr.index.type;

        if (at.hasError()) {
            expr.type = BuiltInType.ERROR;
            return;
        }

        if (!at.isArrayType()) {
            issue(new NotArrayError(expr.array.pos));
            expr.type = BuiltInType.ERROR;
            return;
        }

        expr.type = ((ArrayType) at).elementType;
        if (!it.eq(BuiltInType.INT)) {
            issue(new SubNotIntError(expr.pos));
        }
    }

    @Override
    public void visitCall(Tree.Call expr, ScopeStack ctx) {
        expr.methodExpr.accept(this, ctx);
        expr.type = BuiltInType.ERROR;
        if (expr.methodExpr.type.hasError()) {
            return;
        }

        if (!expr.methodExpr.type.isFuncType()) {
            issue(new NotCallableTypeError(expr.pos, expr.methodExpr.type.toString()));
            return;
        }

        var methodType = (FunType) expr.methodExpr.type;
        expr.type = methodType.returnType;
        // typing args
        var args = expr.args;
        for (var arg : args) {
            arg.accept(this, ctx);
        }

        // check signature compatibility
        if (methodType.argTypes.size() != args.size()) {
            if (expr.methodExpr instanceof Tree.VarSel) {
                issue(new BadArgCountError(expr.pos, ((Tree.VarSel) expr.methodExpr).name,
                        methodType.argTypes.size(), args.size()));
            } else {
                issue(new BadLambdaArgCountError(expr.pos, methodType.argTypes.size(), args.size()));
            }
        }
        for (int i = 0; i < java.lang.Math.min(methodType.argTypes.size(), args.size()); i++) {
            Type t1 = methodType.argTypes.get(i);
            Type t2 = args.get(i).type;
            if (t2.noError() && !t2.subtypeOf(t1)) {
                issue(new BadArgTypeError(args.get(i).pos, i + 1, t2.toString(), t1.toString()));
            }
        }

    }

    private void typeCall(Tree.Call call, boolean thisClass, String className, ScopeStack ctx, boolean requireStatic) {
        // This function becomes unused.
        var clazz = thisClass ? ctx.currentClass() : ctx.getClass(className);
        var methodExpr = (Tree.VarSel) call.methodExpr;
        var symbol = clazz.scope.lookup(methodExpr.name);
        if (symbol.isPresent()) {
            if (symbol.get().isMethodSymbol()) {
                var method = (MethodSymbol) symbol.get();
                call.symbol = method;
                call.type = method.type.returnType;
                if (requireStatic && !method.isStatic()) {
                    issue(new NotClassFieldError(call.methodExpr.pos, methodExpr.name, clazz.type.toString()));
                    return;
                }

                // Cannot call this's member methods in a static method
                if (thisClass && ctx.currentMethod().isStatic() && !method.isStatic()) {
                    issue(new RefNonStaticError(call.methodExpr.pos, ctx.currentMethod().name, method.name));
                }

                // typing args
                var args = call.args;
                for (var arg : args) {
                    arg.accept(this, ctx);
                }

                // check signature compatibility
                if (method.type.arity() != args.size()) {
                    issue(new BadArgCountError(call.pos, method.name, method.type.arity(), args.size()));
                }
                var iter1 = method.type.argTypes.iterator();
                var iter2 = call.args.iterator();
                for (int i = 1; iter1.hasNext() && iter2.hasNext(); i++) {
                    Type t1 = iter1.next();
                    Tree.Expr e = iter2.next();
                    Type t2 = e.type;
                    if (t2.noError() && !t2.subtypeOf(t1)) {
                        issue(new BadArgTypeError(e.pos, i, t2.toString(), t1.toString()));
                    }
                }
            } else {
                issue(new NotClassMethodError(call.methodExpr.pos, methodExpr.name, clazz.type.toString()));
            }
        } else {
            issue(new FieldNotFoundError(call.methodExpr.pos, methodExpr.name, clazz.type.toString()));
        }
    }

    @Override
    public void visitClassTest(Tree.ClassTest expr, ScopeStack ctx) {
        expr.obj.accept(this, ctx);
        expr.type = BuiltInType.BOOL;

        if (!expr.obj.type.isClassType()) {
            issue(new NotClassError(expr.obj.type.toString(), expr.pos));
        }
        var clazz = ctx.lookupClass(expr.is.name);
        if (clazz.isEmpty()) {
            issue(new ClassNotFoundError(expr.pos, expr.is.name));
        } else {
            expr.symbol = clazz.get();
        }
    }

    @Override
    public void visitClassCast(Tree.ClassCast expr, ScopeStack ctx) {
        expr.obj.accept(this, ctx);

        if (!expr.obj.type.isClassType()) {
            issue(new NotClassError(expr.obj.type.toString(), expr.pos));
        }

        var clazz = ctx.lookupClass(expr.to.name);
        if (clazz.isEmpty()) {
            issue(new ClassNotFoundError(expr.pos, expr.to.name));
            expr.type = BuiltInType.ERROR;
        } else {
            expr.symbol = clazz.get();
            expr.type = expr.symbol.type;
        }
    }

    @Override
    public void visitLocalVarDef(Tree.LocalVarDef stmt, ScopeStack ctx) {
        if (stmt.initVal.isEmpty()) return;

        var initVal = stmt.initVal.get();
        localVarDefPos = Optional.ofNullable(stmt.id.pos);
        initVal.accept(this, ctx);
        localVarDefPos = Optional.empty();

        if (stmt.typeLit.isPresent()) {
            var lt = stmt.symbol.type;
            var rt = initVal.type;
            if (lt.hasError() || rt.hasError()) {
                return;
            }
            if (!rt.subtypeOf(lt)) {
                issue(new IncompatBinOpError(stmt.assignPos, lt.toString(), "=", rt.toString()));
            }
        } else {
            // Deduce the type.
            var rt = initVal.type;
            if (initVal.type.eq(BuiltInType.VOID)) {
                issue(new BadVarTypeError(stmt.pos, stmt.name));
                rt = BuiltInType.ERROR;
            }
            stmt.symbol.setType(rt);
        }
    }

    @Override
    public void visitLambda(Tree.Lambda lambda, ScopeStack ctx) {
        Type returnType;
        var argTypes = new ArrayList<Type>();
        boolean hasError = false;
        lambdaLevel++; // for return type check
        ctx.open(lambda.scope);
        for (var param : lambda.params) {
            param.accept(this, ctx);
            argTypes.add(param.symbol.type);
            if (param.symbol.type.hasError())
                hasError = true;
        }
        if (lambda.hasReturnExpr()) {
            ctx.open(lambda.scope.nestedLocalScope());
            lambda.returnExpr.get().accept(this, ctx);
            returnType = lambda.returnExpr.get().type;
            ctx.close();
        } else {
            // Open local scope in visitBlock.
            lambda.body.get().accept(this, ctx);
            returnType = lambda.body.get().returnType;
            if (returnType == null) {
                returnType = BuiltInType.VOID;
            }
            if (!returnType.isVoidType() && !lambda.body.get().returns) {
                issue(new MissingReturnError(lambda.body.get().pos));
            }
            if (returnType.isIncompatible()) {
                issue(new IncompatRetTypeError(lambda.body.get().pos));
            }
        }
        ctx.close();
        lambdaLevel--;
        if (hasError || returnType.hasError()) {
            lambda.type = BuiltInType.ERROR;
        } else {
            var lambdaType = new FunType(returnType, argTypes);
            lambda.type = lambdaType;
            lambda.symbol.setType(lambdaType);
        }
    }

    // Only usage: check if an initializer cyclically refers to the declared variable, e.g. var x = x + 1
    private Optional<Pos> localVarDefPos = Optional.empty();
}

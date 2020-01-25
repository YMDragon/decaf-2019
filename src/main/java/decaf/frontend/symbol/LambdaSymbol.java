package decaf.frontend.symbol;

import decaf.frontend.scope.ClassScope;
import decaf.frontend.scope.FormalScope;
import decaf.frontend.scope.LambdaScope;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.type.FunType;

/**
 * Lambda symbol, representing a lambda expression definition.
 */
public final class LambdaSymbol extends Symbol {

    public FunType type;

    /**
     * Associated lambda scope of the lambda expression parameters.
     */
    public final LambdaScope scope;

    public final ClassSymbol owner;

    public LambdaSymbol(LambdaScope scope, Pos pos, ClassSymbol owner) {
        super("lambda@" + pos, null, pos);
        this.scope = scope;
        this.owner = owner;
    }

    public void setType(FunType type) {
        this.type = type;
        super.type = type;
    }

    @Override
    public ClassScope domain() {
        return (ClassScope) definedIn;
    }

    @Override
    public boolean isLambdaSymbol() {
        return true;
    }

    @Override
    protected String str() {
        return String.format("function %s : %s", name, type);
    }
}

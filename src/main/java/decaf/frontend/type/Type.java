package decaf.frontend.type;

import java.util.ArrayList;

/**
 * Type.
 * <p>
 * Decaf has a very simple type system, consisting of:
 * <ol>
 *     <li>basic types: int, bool, string (and void)</li>
 *     <li>array types</li>
 *     <li>class types</li>
 *     <li>function types (cannot be expressed in programs, but we use them to type check function calls)</li>
 * </ol>
 * <p>
 * Types are resolved by {@link decaf.frontend.typecheck.Typer}.
 *
 * @see BuiltInType
 * @see ClassType
 * @see ArrayType
 * @see FunType
 */
public abstract class Type {

    /**
     * Is this type int, bool, or string?
     *
     * @see BuiltInType#isBaseType
     */
    public boolean isBaseType() {
        return false;
    }

    public boolean isArrayType() {
        return false;
    }

    public boolean isClassType() {
        return false;
    }

    public boolean isFuncType() {
        return false;
    }

    public boolean isVoidType() {
        return false;
    }

    public boolean noError() {
        return true;
    }

    public boolean isIncompatible() {
        return false;
    }

    public boolean hasError() {
        return !noError();
    }

    /**
     * Tell if this type <em>is subtype of</em> another type.
     * <p>
     * Let {@code t1} {@literal <:} {@code t2} denote that type {@code t1} is subtype of {@code t2}. Rules:
     * <ol>
     *     <li>reflexive: {@code t} {@literal <:} {@code t}</li>
     *     <li>transitive: {@code t1} {@literal <:} {@code t3} if
     *          {@code t1} {@literal <:} {@code t2} and {@code t2} {@literal <:} {@code t3}</li>
     *     <li>error is special: {@code error} {@literal <:} {@code t}, {@code t} {@literal <:} {@code error}</li>
     *     <li>incmp is special: {@code t} {@literal <:} {@code incmp}</li>
     *     <li>null is an object: {@code null} {@literal <:} {@code class c} for every class {@code c}</li>
     *     <li>class inheritance: {@code class c1} {@literal <:} {@code class c2} if {@code c1} extends {@code c2}</li>
     *     <li>function: {@code (t1, t2, ..., tn) => t} {@literal <:} {@code (s1, s2, ..., sn) => s} if
     *          {@code t} {@literal <:} {@code s} and {@code si} {@literal <:} {@code ti} for every {@code i}</li>
     * </ol>
     *
     * @param that another type
     * @return subtype checking result
     */
    public abstract boolean subtypeOf(Type that);

    /**
     * Tell if two types are equivalent.
     *
     * @param that another type
     * @return type equivalent checking result
     */
    public abstract boolean eq(Type that);

    /**
     * Upper bound and lower bound of two types.
     *
     * @param that another type
     * @return upper bound or lower bound
     */
    public Type upperBound(Type that) {
        if (hasError())
            return that;
        if (that.hasError())
            return this;
        if (subtypeOf(that))
            return that;
        if (that.subtypeOf(this))
            return this;
        if (isClassType()) {
            ClassType result = (ClassType) this;
            while (result.superType.isPresent()) {
                result = result.superType.get();
                if (that.subtypeOf(result)) {
                    return result;
                }
            }
        }
        if (isFuncType() && that.isFuncType()) {
            assert this instanceof FunType;
            FunType thisFunc = (FunType) this;
            FunType thatFunc = (FunType) that;
            if (thisFunc.argTypes.size() != thatFunc.argTypes.size()) {
                return BuiltInType.INCMP;
            }
            Type returnType = thisFunc.returnType.upperBound(thatFunc.returnType);
            if (returnType.isIncompatible()) {
                return BuiltInType.INCMP;
            }
            var argTypes = new ArrayList<Type>();
            for (int i = 0; i < thisFunc.argTypes.size(); i++) {
                argTypes.add(thisFunc.argTypes.get(i).lowerBound(thatFunc.argTypes.get(i)));
                if (argTypes.get(i).isIncompatible()) {
                    return BuiltInType.INCMP;
                }
            }
            return new FunType(returnType, argTypes);
        }
        return BuiltInType.INCMP;
    }

    public Type lowerBound(Type that) {
        if (hasError())
            return that;
        if (that.hasError())
            return this;
        if (isIncompatible() || that.isIncompatible())
            return BuiltInType.INCMP;
        if (subtypeOf(that))
            return this;
        if (that.subtypeOf(this))
            return that;
        if (isFuncType() && that.isFuncType()) {
            assert this instanceof FunType;
            FunType thisFunc = (FunType) this;
            FunType thatFunc = (FunType) that;
            if (thisFunc.argTypes.size() != thatFunc.argTypes.size()) {
                return BuiltInType.INCMP;
            }
            Type returnType = thisFunc.returnType.lowerBound(thatFunc.returnType);
            if (returnType.isIncompatible()) {
                return BuiltInType.INCMP;
            }
            var argTypes = new ArrayList<Type>();
            for (int i = 0; i < thisFunc.argTypes.size(); i++) {
                argTypes.add(thisFunc.argTypes.get(i).upperBound(thatFunc.argTypes.get(i)));
                if (argTypes.get(i).isIncompatible()) {
                    return BuiltInType.INCMP;
                }
            }
            return new FunType(returnType, argTypes);
        }
        return BuiltInType.INCMP;
    }

    public abstract String toString();
}

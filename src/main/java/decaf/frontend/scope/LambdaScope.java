package decaf.frontend.scope;

import decaf.frontend.symbol.LambdaSymbol;
import decaf.frontend.symbol.VarSymbol;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lambda scope: stores parameter variable symbols. It is owned by a lambda expression symbol.
 */
public class LambdaScope extends Scope {

    public LambdaScope(Scope parent) {
        super(Kind.LAMBDA);
        assert parent.isLocalScope();
        ((LocalScope) parent).nestedLocalOrLambdaScopes().add(this);
        this.parent = parent;
    }

    public LambdaSymbol getOwner() {
        return owner;
    }

    public void setOwner(LambdaSymbol owner) {
        this.owner = owner;
    }

    @Override
    public boolean isLambdaScope() {
        return true;
    }

    /**
     * Get the local scope associated with the method body.
     *
     * @return local scope
     */
    public LocalScope nestedLocalScope() {
        return nested;
    }

    /**
     * Set the local scope.
     *
     * @param scope local scope
     */
    void setNested(LocalScope scope) {
        nested = scope;
    }

    /**
     * Capture a VarSymbol.
     *
     * @param varSymbol the VarSymbol to be captured
     */
    public void capture(VarSymbol varSymbol) {
        capturedVar.put(varSymbol.name, varSymbol);
    }

    /**
     * Get captured VarSymbols.
     *
     * @return list of VarSymbols
     */
    public List<VarSymbol> capturedVars() {
        var list = new ArrayList<>(capturedVar.values());
        Collections.sort(list);
        return list;
    }

    private LambdaSymbol owner;

    private LocalScope nested;

    public final Scope parent;

    private Map<String, VarSymbol> capturedVar = new TreeMap<>();
}

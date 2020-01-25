package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：arguments in function type must be non-void known type<br>
 * PA2
 */
public class BadFunArgTypeError extends DecafError {

    public BadFunArgTypeError(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "arguments in function type must be non-void known type";
    }

}

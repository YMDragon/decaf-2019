package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：cannot assign value to captured variables in lambda expression<br>
 * PA2
 */
public class AssignCaptureError extends DecafError {

    public AssignCaptureError(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "cannot assign value to captured variables in lambda expression";
    }

}

package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：lambda expression expects 1 argument(s) but 3 given<br>
 * PA2
 */
public class BadLambdaArgCountError extends DecafError {

    private int expect;

    private int count;

    public BadLambdaArgCountError(Pos pos, int expect, int count) {
        super(pos);
        this.expect = expect;
        this.count = count;
    }

    @Override
    protected String getErrMsg() {
        return "lambda expression expects " + expect + " argument(s) but " + count + " given";
    }
}

package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：string is not a callable type<br>
 * PA2
 */
public class NotCallableTypeError extends DecafError {

    private String name;

    public NotCallableTypeError(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return name + " is not a callable type";
    }

}

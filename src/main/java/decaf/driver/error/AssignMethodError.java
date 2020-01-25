package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：cannot assign value to class member method 'sf'<br>
 * PA2
 */
public class AssignMethodError extends DecafError {

    private String name;

    public AssignMethodError(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return "cannot assign value to class member method '" + name + "'";
    }

}

package decaf.printing;

public abstract class PrettyPrinter<T> {
    protected final IndentPrinter printer;

    public PrettyPrinter(IndentPrinter printer) {
        this.printer = printer;
    }

    public abstract void pretty(T t);
}

package dev.hc224.slashlib.context;

/**
 * Used when data couldn't be collected for a context builder when calling
 *  {@link ChatContextBuilder#collectData()}
 */
public class DataMissingException extends RuntimeException {
    private final ContextBuilder builder;

    public DataMissingException(ContextBuilder builder, String reason) {
        super(reason);
        this.builder = builder;
    }

    /**
     * Don't fill in the stack trace
     * @return this instance
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
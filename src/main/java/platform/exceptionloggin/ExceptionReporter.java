package platform.exceptionloggin;

public interface ExceptionReporter {
    void report(Throwable error, FailureContext context);

    default void report(Throwable error) {
        report(error, FailureContext.empty());
    }
}


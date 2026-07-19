package platform.exceptionloggin;

final class ReportingGuard {
    private ReportingGuard() {
    }

    static void report(ExceptionReporter reporter, Throwable error, FailureContext context) {
        try {
            reporter.report(error, context);
        } catch (Throwable reportingFailure) {
            rethrowFatal(reportingFailure);
            addSuppressed(error, reportingFailure);
        }
    }

    static void report(ExceptionReporter reporter, Throwable error) {
        report(reporter, error, FailureContext.empty());
    }

    static void addSuppressed(Throwable error, Throwable reportingFailure) {
        if (error == null || reportingFailure == null || error == reportingFailure) {
            return;
        }
        try {
            error.addSuppressed(reportingFailure);
        } catch (RuntimeException ignored) {
            // The original application failure must remain authoritative.
        }
    }

    static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (error instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }
}

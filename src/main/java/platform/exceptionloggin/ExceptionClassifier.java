package platform.exceptionloggin;

public interface ExceptionClassifier {
    ErrorCategory classify(Throwable error);
}


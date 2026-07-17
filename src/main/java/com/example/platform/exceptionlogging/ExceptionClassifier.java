package com.example.platform.exceptionlogging;

public interface ExceptionClassifier {
    ErrorCategory classify(Throwable error);
}


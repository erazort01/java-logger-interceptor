package com.example.platform.exceptionlogging;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
final class GlobalExceptionHandler {
    private final ExceptionReporter reporter;
    private final ExceptionClassifier classifier;

    GlobalExceptionHandler(ExceptionReporter reporter, ExceptionClassifier classifier) {
        this.reporter = reporter;
        this.classifier = classifier;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException error) {
        reporter.report(error);
        return response(error.getStatus(), error.getCode(), error.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception error) {
        reporter.report(error);
        ErrorCategory category = classifier.classify(error);
        if (category == ErrorCategory.AUTH) {
            return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                    "No se ha podido autenticar la solicitud");
        }
        if (category == ErrorCategory.DATABASE || category == ErrorCategory.CONNECTIVITY) {
            return response(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Un servicio necesario no está disponible temporalmente");
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Se ha producido un error interno");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, MDC.get("correlationId")));
    }
}

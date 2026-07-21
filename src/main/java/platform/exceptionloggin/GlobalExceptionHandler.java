package platform.exceptionloggin;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.regex.Pattern;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
final class GlobalExceptionHandler {
    private static final Pattern PUBLIC_ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final ExceptionReporter reporter;
    private final ExceptionClassifier classifier;
    private final ContextSanitizer sanitizer;
    private final TraceContext traceContext;

    GlobalExceptionHandler(ExceptionReporter reporter,
                           ExceptionClassifier classifier,
                           ContextSanitizer sanitizer,
                           TraceContext traceContext) {
        this.reporter = reporter;
        this.classifier = classifier;
        this.sanitizer = sanitizer;
        this.traceContext = traceContext;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException error) {
        ReportingGuard.report(reporter, error);
        HttpStatus status = error.getStatus().is4xxClientError()
                ? error.getStatus()
                : HttpStatus.UNPROCESSABLE_ENTITY;
        String code = PUBLIC_ERROR_CODE.matcher(error.getCode() == null ? "" : error.getCode()).matches()
                ? error.getCode()
                : "BUSINESS_ERROR";
        return response(status, code, sanitizer.sanitizeText(error.getPublicMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception error) {
        if (error instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
            ReportingGuard.report(reporter, error);
            return authenticationFailure();
        }
        if (error instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            return response(errorResponse.getStatusCode(), "REQUEST_REJECTED", "La solicitud no es válida");
        }
        ReportingGuard.report(reporter, error);
        ErrorCategory category = classifySafely(error);
        if (category == ErrorCategory.AUTH) {
            return authenticationFailure();
        }
        if (category == ErrorCategory.DATABASE || category == ErrorCategory.CONNECTIVITY) {
            return response(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Un servicio necesario no está disponible temporalmente");
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Se ha producido un error interno");
    }

    private ResponseEntity<ApiError> authenticationFailure() {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "No se ha podido autenticar la solicitud");
    }

    private ResponseEntity<ApiError> response(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, currentTraceId()));
    }

    private String currentTraceId() {
        try {
            return traceContext.currentTraceId().orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ErrorCategory classifySafely(Exception error) {
        try {
            return classifier.classify(error);
        } catch (Throwable classificationFailure) {
            ReportingGuard.rethrowFatal(classificationFailure);
            ReportingGuard.addSuppressed(error, classificationFailure);
            return ErrorCategory.UNEXPECTED;
        }
    }
}

package platform.exceptionloggin;

import java.time.Instant;

public record ExceptionLogEvent(
        Instant timestamp,
        String microservice,
        ErrorCategory category,
        String errorCode,
        String exceptionType,
        String message,
        String rootCause,
        String table,
        String operation,
        String correlationId,
        String traceId,
        Object metadata,
        String stackTrace) {
}

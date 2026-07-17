package com.example.platform.exceptionlogging;

import java.time.Instant;
import java.util.Map;

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
        String objectType,
        Object objectSnapshot,
        Map<String, Object> metadata) {
}


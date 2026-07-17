package com.example.platform.exceptionlogging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Slf4jExceptionReporter implements ExceptionReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("exception.audit");

    private final ExceptionLoggingProperties properties;
    private final ExceptionClassifier classifier;
    private final ObjectMapper objectMapper;
    private final ContextSanitizer sanitizer;
    private final ReportedExceptionRegistry registry;

    Slf4jExceptionReporter(ExceptionLoggingProperties properties,
                           ExceptionClassifier classifier,
                           ObjectMapper objectMapper,
                           ReportedExceptionRegistry registry) {
        this.properties = properties;
        this.classifier = classifier;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.sanitizer = new ContextSanitizer(objectMapper, properties.getAdditionalSensitiveFields());
    }

    @Override
    public void report(Throwable error, FailureContext context) {
        if (!properties.isEnabled() || !registry.markIfFirst(error)) {
            return;
        }
        ExceptionLogEvent event = createEvent(error, context);
        LOGGER.error("exception_event={}", serialize(event));
    }

    ExceptionLogEvent createEvent(Throwable error, FailureContext context) {
        Throwable root = rootCause(error);
        Map<String, Object> metadata = new LinkedHashMap<>(context.metadata());
        if (context.failedObject() != null) {
            metadata.put("failedObjectType", context.failedObject().getClass().getName());
            metadata.put("failedObject", context.failedObject());
        }
        Object sanitizedMetadata = sanitizer.sanitize(metadata);
        return new ExceptionLogEvent(
                Instant.now(), properties.getApplicationName(), classifier.classify(error), errorCode(error),
                error.getClass().getName(), sanitizer.sanitizeText(safeMessage(error)),
                sanitizer.sanitizeText(safeMessage(root)), context.table(),
                context.operation(), MDC.get("correlationId"), MDC.get("traceId"),
                sanitizedMetadata, properties.isIncludeStacktrace() ? sanitizedStackTrace(error) : null);
    }

    private String serialize(ExceptionLogEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException error) {
            return "{\"serializationError\":\"" + error.getClass().getSimpleName() + "\"}";
        }
    }

    private static String errorCode(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof BusinessException businessException) {
                return businessException.getCode();
            }
        }
        return null;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null ? null : error.getMessage();
    }

    private String sanitizedStackTrace(Throwable error) {
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return sanitizer.sanitizeText(buffer.toString());
    }
}

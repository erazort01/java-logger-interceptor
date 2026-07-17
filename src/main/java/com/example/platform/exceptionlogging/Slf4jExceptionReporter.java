package com.example.platform.exceptionlogging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;

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
        this.sanitizer = new ContextSanitizer(
                objectMapper, properties.getSensitiveFields(), properties.getMaxObjectLength());
    }

    @Override
    public void report(Throwable error, FailureContext context) {
        if (!properties.isEnabled() || !registry.markIfFirst(error)) {
            return;
        }
        Throwable root = rootCause(error);
        Object snapshot = properties.isCaptureObject() ? sanitizer.sanitize(context.failedObject()) : null;
        ExceptionLogEvent event = new ExceptionLogEvent(
                Instant.now(), properties.getApplicationName(), classifier.classify(error), errorCode(error),
                error.getClass().getName(), safeMessage(error), safeMessage(root), context.table(),
                context.operation(), MDC.get("correlationId"), MDC.get("traceId"),
                context.failedObject() == null ? null : context.failedObject().getClass().getName(),
                snapshot, context.metadata());
        String json = serialize(event);
        if (properties.isIncludeStacktrace()) {
            LOGGER.error("exception_event={}", json, error);
        } else {
            LOGGER.error("exception_event={}", json);
        }
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
}


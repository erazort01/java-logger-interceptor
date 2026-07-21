package platform.exceptionloggin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final TraceContext traceContext;

    Slf4jExceptionReporter(ExceptionLoggingProperties properties,
                           ExceptionClassifier classifier,
                           ObjectMapper objectMapper,
                           ContextSanitizer sanitizer,
                           ReportedExceptionRegistry registry,
                           TraceContext traceContext) {
        this.properties = properties;
        this.classifier = classifier;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.sanitizer = sanitizer;
        this.traceContext = traceContext;
    }

    @Override
    public void report(Throwable error, FailureContext context) {
        if (error == null || !properties.isEnabled()) {
            return;
        }
        try {
            if (!registry.markIfFirst(error)) {
                return;
            }
            ExceptionLogEvent event = createEvent(error, context == null ? FailureContext.empty() : context);
            LOGGER.error("exception_event={}", serialize(event));
        } catch (Throwable reportingFailure) {
            ReportingGuard.rethrowFatal(reportingFailure);
            ReportingGuard.addSuppressed(error, reportingFailure);
            logReportingFailure(error, reportingFailure);
        }
    }

    ExceptionLogEvent createEvent(Throwable error, FailureContext context) {
        Throwable root = ThrowableChain.rootCause(error);
        Map<String, Object> metadata = new LinkedHashMap<>(context.metadata());
        if (context.failedObject() != null) {
            metadata.put("failedObjectType", context.failedObject().getClass().getName());
            metadata.put("failedObject", context.failedObject());
        }
        Object sanitizedMetadata = sanitizer.sanitize(metadata);
        String traceId = traceContext.currentTraceId().orElse(null);
        return new ExceptionLogEvent(
                Instant.now(), sanitizer.sanitizeText(properties.getApplicationName()), classifier.classify(error),
                sanitizer.sanitizeText(errorCode(error)), sanitizer.sanitizeText(error.getClass().getName()),
                sanitizer.sanitizeText(safeMessage(error)), sanitizer.sanitizeText(safeMessage(root)),
                sanitizer.sanitizeText(context.table()), sanitizer.sanitizeText(context.operation()),
                traceId, traceId,
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
        for (Throwable current : ThrowableChain.from(error)) {
            if (current instanceof BusinessException businessException) {
                return businessException.getCode();
            }
        }
        return null;
    }

    private static String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null ? null : error.getMessage();
    }

    private String sanitizedStackTrace(Throwable error) {
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return sanitizer.sanitizeText(buffer.toString(), properties.getMaxStackTraceLength());
    }

    private static void logReportingFailure(Throwable original, Throwable reportingFailure) {
        try {
            LOGGER.warn("exception_reporting_failed originalType={} reportingFailureType={}",
                    original.getClass().getName(), reportingFailure.getClass().getName());
        } catch (Throwable fallbackFailure) {
            ReportingGuard.rethrowFatal(fallbackFailure);
        }
    }
}

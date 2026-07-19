package platform.exceptionloggin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Slf4jExceptionReporterTest {
    @Test
    void placesTheCompleteSanitizedFailedObjectInsideMetadata() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        properties.setApplicationName("example-service");
        properties.setIncludeStacktrace(true);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TraceContext traceContext = new DefaultTraceContext(() -> "generated-trace-123");
        Slf4jExceptionReporter reporter = new Slf4jExceptionReporter(
                properties, new DefaultExceptionClassifier(), objectMapper,
                new ContextSanitizer(objectMapper, properties), new ReportedExceptionRegistry(), traceContext);

        ExceptionLogEvent event = reporter.createEvent(
                new IllegalStateException("failure for ana@example.com"),
                FailureContext.builder()
                        .table("example_records")
                        .operation("INSERT")
                        .failedObject(Map.of(
                                "id", "record-1",
                                "owner", Map.of("name", "Ana", "password", "secret")))
                        .metadata("tenant", "europe")
                        .build());

        JsonNode metadata = (JsonNode) event.metadata();
        assertThat(metadata.get("tenant").asText()).isEqualTo("europe");
        assertThat(metadata.get("failedObjectType").asText()).contains("Map");
        assertThat(metadata.get("failedObject").get("id").asText()).isEqualTo("record-1");
        assertThat(metadata.get("failedObject").get("owner").get("name").asText())
                .isEqualTo("[REDACTED]");
        assertThat(metadata.get("failedObject").get("owner").get("password").asText())
                .isEqualTo("[REDACTED]");
        assertThat(event.message()).doesNotContain("ana@example.com");
        assertThat(event.stackTrace()).doesNotContain("ana@example.com");
    }

    @Test
    void reportingFailureNeverReplacesTheOriginalException() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        IllegalStateException original = new IllegalStateException("original");
        IllegalArgumentException classifierFailure = new IllegalArgumentException("classifier failed");
        Slf4jExceptionReporter reporter = new Slf4jExceptionReporter(
                properties, error -> { throw classifierFailure; }, objectMapper,
                new ContextSanitizer(objectMapper, properties), new ReportedExceptionRegistry(),
                new DefaultTraceContext(() -> "generated-trace-123"));

        reporter.report(original);

        assertThat(original.getSuppressed()).containsExactly(classifierFailure);
    }
}

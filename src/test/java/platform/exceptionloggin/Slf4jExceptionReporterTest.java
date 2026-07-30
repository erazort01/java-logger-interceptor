package platform.exceptionloggin;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Slf4jExceptionReporterTest {
    @Test
    void placesTheCompleteSanitizedFailedObjectInsideMetadata() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        properties.setApplicationName("example-service");
        properties.setIncludeStacktrace(true);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
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
        assertThat(metadata.get("tenant").asString()).isEqualTo("europe");
        assertThat(metadata.get("failedObjectType").asString()).contains("Map");
        assertThat(metadata.get("failedObject").get("id").asString()).isEqualTo("record-1");
        assertThat(metadata.get("failedObject").get("owner").get("name").asString())
                .isEqualTo("[REDACTED]");
        assertThat(metadata.get("failedObject").get("owner").get("password").asString())
                .isEqualTo("[REDACTED]");
        assertThat(event.message()).doesNotContain("ana@example.com");
        assertThat(event.stackTrace()).doesNotContain("ana@example.com");
    }

    @Test
    void reportingFailureNeverReplacesTheOriginalException() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
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

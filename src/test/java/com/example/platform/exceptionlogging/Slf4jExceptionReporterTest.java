package com.example.platform.exceptionlogging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Slf4jExceptionReporterTest {
    @Test
    void placesTheCompleteSanitizedFailedObjectInsideMetadata() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        properties.setApplicationName("orders-service");
        Slf4jExceptionReporter reporter = new Slf4jExceptionReporter(
                properties, new DefaultExceptionClassifier(), new ObjectMapper(),
                new ReportedExceptionRegistry());

        ExceptionLogEvent event = reporter.createEvent(
                new IllegalStateException("failure for ana@example.com"),
                FailureContext.builder()
                        .table("orders")
                        .operation("INSERT")
                        .failedObject(Map.of(
                                "id", "order-1",
                                "customer", Map.of("name", "Ana", "password", "secret")))
                        .metadata("tenant", "europe")
                        .build());

        JsonNode metadata = (JsonNode) event.metadata();
        assertThat(metadata.get("tenant").asText()).isEqualTo("europe");
        assertThat(metadata.get("failedObjectType").asText()).contains("Map");
        assertThat(metadata.get("failedObject").get("id").asText()).isEqualTo("order-1");
        assertThat(metadata.get("failedObject").get("customer").get("name").asText())
                .isEqualTo("[REDACTED]");
        assertThat(metadata.get("failedObject").get("customer").get("password").asText())
                .isEqualTo("[REDACTED]");
        assertThat(event.message()).doesNotContain("ana@example.com");
        assertThat(event.stackTrace()).doesNotContain("ana@example.com");
    }
}

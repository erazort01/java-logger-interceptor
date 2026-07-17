package com.example.platform.exceptionlogging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSanitizerTest {
    @Test
    void redactsSensitiveFieldsRecursively() {
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), Set.of());

        Object result = sanitizer.sanitize(Map.of(
                "username", "ana",
                "password", "secret",
                "nested", Map.of("token", "abc")));

        JsonNode tree = (JsonNode) result;
        assertThat(tree.get("username").asText()).isEqualTo("[REDACTED]");
        assertThat(tree.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(tree.get("nested").get("token").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void mandatoryFieldsCannotBeRemovedAndAdditionalFieldsCanOnlyExtendMasking() {
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), Set.of("internalReference"));
        JsonNode tree = (JsonNode) sanitizer.sanitize(Map.of(
                "password", "mandatory",
                "internalReference", "additional"));

        assertThat(tree.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(tree.get("internalReference").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void masksSensitivePatternsInsideFreeText() {
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), Set.of());

        assertThat(sanitizer.sanitizeText(
                        "Contact ana@example.com with Bearer abc.123 and password=super-secret"))
                .doesNotContain("ana@example.com", "abc.123", "super-secret")
                .contains("[REDACTED]");
    }
}

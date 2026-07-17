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
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), Set.of("password", "token"), 500);

        Object result = sanitizer.sanitize(Map.of(
                "username", "ana",
                "password", "secret",
                "nested", Map.of("token", "abc")));

        JsonNode tree = (JsonNode) result;
        assertThat(tree.get("username").asText()).isEqualTo("ana");
        assertThat(tree.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(tree.get("nested").get("token").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void truncatesLargeObjects() {
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), Set.of(), 10);
        assertThat(sanitizer.sanitize(Map.of("value", "a very long value")))
                .isInstanceOf(String.class)
                .asString().endsWith("...[TRUNCATED]");
    }
}

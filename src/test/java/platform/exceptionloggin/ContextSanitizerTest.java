package platform.exceptionloggin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSanitizerTest {
    @Test
    void redactsSensitiveFieldsRecursively() {
        ContextSanitizer sanitizer = sanitizer(Set.of());

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
        ContextSanitizer sanitizer = sanitizer(Set.of("internalReference"));
        JsonNode tree = (JsonNode) sanitizer.sanitize(Map.of(
                "password", "mandatory",
                "internalReference", "additional"));

        assertThat(tree.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(tree.get("internalReference").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void masksSensitivePatternsInsideFreeText() {
        ContextSanitizer sanitizer = sanitizer(Set.of());

        assertThat(sanitizer.sanitizeText(
                        "Contact ana@example.com with Bearer abc.123 and password=super-secret"))
                .doesNotContain("ana@example.com", "abc.123", "super-secret")
                .contains("[REDACTED]");
    }

    @Test
    void masksAuthorizationQuotedSecretsAndPersonalIdentifiers() {
        ContextSanitizer sanitizer = sanitizer(Set.of());

        String result = sanitizer.sanitizeText("Authorization: Basic dXNlcjpwYXNz "
                + "password=\"secret with spaces\" phone=+34600111222 nif=12345678Z");

        assertThat(result)
                .doesNotContain("dXNlcjpwYXNz", "secret with spaces", "+34600111222", "12345678Z")
                .contains("[REDACTED]");
    }

    @Test
    void limitsTextAndMetadataTraversal() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        properties.setMaxMessageLength(24);
        properties.setMaxMetadataNodes(3);
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), properties);

        assertThat(sanitizer.sanitizeText("a".repeat(100))).hasSize(24).endsWith("[TRUNCATED]");
        assertThat(sanitizer.sanitize(Map.of("one", "1", "two", "2", "three", "3")).toString())
                .contains("_exceptionLoggingTruncated");
    }

    @Test
    void redactsAnIncompletePrivateKeyAfterTextTruncation() {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        properties.setMaxMessageLength(48);
        ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), properties);

        String result = sanitizer.sanitizeText("-----BEGIN PRIVATE KEY-----" + "A".repeat(200));

        assertThat(result).isEqualTo("[REDACTED]");
    }

    private static ContextSanitizer sanitizer(Set<String> additionalSensitiveFields) {
        ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
        properties.setAdditionalSensitiveFields(additionalSensitiveFields);
        return new ContextSanitizer(new ObjectMapper(), properties);
    }
}

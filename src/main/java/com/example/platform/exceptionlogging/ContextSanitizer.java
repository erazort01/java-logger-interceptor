package com.example.platform.exceptionlogging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Locale;
import java.util.Set;

final class ContextSanitizer {
    private static final String REDACTED = "[REDACTED]";

    private final ObjectMapper objectMapper;
    private final Set<String> sensitiveFields;
    private final int maxLength;

    ContextSanitizer(ObjectMapper objectMapper, Set<String> sensitiveFields, int maxLength) {
        this.objectMapper = objectMapper;
        this.sensitiveFields = sensitiveFields.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.maxLength = maxLength;
    }

    Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            redact(tree);
            String json = objectMapper.writeValueAsString(tree);
            if (json.length() <= maxLength) {
                return tree;
            }
            return json.substring(0, maxLength) + "...[TRUNCATED]";
        } catch (IllegalArgumentException | JsonProcessingException error) {
            return "[UNSERIALIZABLE:" + value.getClass().getSimpleName() + "]";
        }
    }

    private void redact(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (sensitiveFields.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    entry.setValue(TextNode.valueOf(REDACTED));
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }
}

package com.example.platform.exceptionlogging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ContextSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> MANDATORY_SENSITIVE_FIELDS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "accesstoken", "refreshtoken",
            "authorization", "apikey", "privatekey", "accesskey", "clientsecret", "credential",
            "cvv", "cvc", "pin", "iban", "bankaccount", "cardnumber", "creditcard", "ssn",
            "nif", "nie", "passport", "email", "phone", "mobile", "address", "name",
            "birthdate", "dateofbirth", "taxid", "documentnumber", "ipaddress", "geolocation");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}(?![\\w.-])");
    private static final Pattern IBAN = Pattern.compile(
            "(?i)\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]){11,30}\\b");
    private static final Pattern PAYMENT_CARD = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|"
                    + "authorization|api[_-]?key|private[_-]?key|client[_-]?secret|cvv|cvc|pin|iban)"
                    + "\\s*[:=]\\s*[^\\s,;]+"
    );

    private final ObjectMapper objectMapper;
    private final Set<String> sensitiveFields;

    ContextSanitizer(ObjectMapper objectMapper, Set<String> additionalSensitiveFields) {
        this.objectMapper = objectMapper;
        LinkedHashSet<String> fields = new LinkedHashSet<>(MANDATORY_SENSITIVE_FIELDS);
        additionalSensitiveFields.stream()
                .map(ContextSanitizer::normalize)
                .forEach(fields::add);
        this.sensitiveFields = Set.copyOf(fields);
    }

    Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            redact(tree);
            return tree;
        } catch (IllegalArgumentException error) {
            return "[UNSERIALIZABLE:" + value.getClass().getSimpleName() + "]";
        }
    }

    String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = replace(EMAIL, value);
        sanitized = replace(IBAN, sanitized);
        sanitized = replace(PAYMENT_CARD, sanitized);
        sanitized = replace(BEARER, sanitized);
        sanitized = replace(JWT, sanitized);
        return SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1=" + REDACTED);
    }

    private void redact(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (isSensitiveField(entry.getKey())) {
                    entry.setValue(TextNode.valueOf(REDACTED));
                } else if (entry.getValue().isTextual()) {
                    entry.setValue(TextNode.valueOf(sanitizeText(entry.getValue().textValue())));
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                JsonNode value = node.get(index);
                if (value.isTextual()) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) node)
                            .set(index, TextNode.valueOf(sanitizeText(value.textValue())));
                } else {
                    redact(value);
                }
            }
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String normalized = normalize(fieldName);
        return sensitiveFields.stream().anyMatch(normalized::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String replace(Pattern pattern, String value) {
        return pattern.matcher(value).replaceAll(Matcher.quoteReplacement(REDACTED));
    }
}

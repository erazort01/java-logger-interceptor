package platform.exceptionloggin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ContextSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final String TRUNCATED = "[TRUNCATED]";
    private static final Set<String> MANDATORY_SENSITIVE_FIELDS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "accesstoken", "refreshtoken",
            "authorization", "apikey", "privatekey", "accesskey", "clientsecret", "credential",
            "pin", "ssn", "cookie", "setcookie", "sessionid", "sessionkey", "cardnumber", "cvv",
            "nif", "nie", "passport", "email", "phone", "mobile", "address", "name",
            "birthdate", "dateofbirth", "taxid", "documentnumber", "ipaddress", "geolocation");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}(?![\\w.-])");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)\\b(?:authorization|proxy-authorization)\\s*[:=]\\s*(?:basic|bearer)\\s+[^\\s,;]+");
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(?i)\\b(?:Basic|Bearer)\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern COOKIE = Pattern.compile(
            "(?i)\\b(?:cookie|set-cookie)\\s*:\\s*[^\\r\\n]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?is)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?(?:"
                    + "-----END [^-\\r\\n]*PRIVATE KEY-----|\\z)");
    private static final Pattern SPANISH_ID = Pattern.compile(
            "(?i)\\b(?:[XYZ]\\d{7}[A-Z]|\\d{8}[A-Z])\\b");
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\w.])\\+?\\d(?:[\\d .()-]{7,}\\d)(?!\\w)");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|access[_-]?token|refresh[_-]?token|"
                    + "authorization|api[_-]?key|private[_-]?key|client[_-]?secret|pin|"
                    + "cookie|session[_-]?id|session[_-]?key|card[_-]?number|cvv|ssn|nif|nie|"
                    + "passport|email|phone|mobile|address|birth[_-]?date|date[_-]?of[_-]?birth|"
                    + "tax[_-]?id|document[_-]?number|ip[_-]?address|geolocation)"
                    + "\\s*[:=]\\s*(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\r\\n,;&]+)"
    );

    private final ObjectMapper objectMapper;
    private final Set<String> sensitiveFields;
    private final int maxMessageLength;
    private final int maxMetadataDepth;
    private final int maxMetadataNodes;

    ContextSanitizer(ObjectMapper objectMapper, ExceptionLoggingProperties properties) {
        this.objectMapper = objectMapper;
        LinkedHashSet<String> fields = new LinkedHashSet<>(MANDATORY_SENSITIVE_FIELDS);
        properties.getAdditionalSensitiveFields().stream()
                .map(ContextSanitizer::normalize)
                .forEach(fields::add);
        this.sensitiveFields = Set.copyOf(fields);
        this.maxMessageLength = properties.getMaxMessageLength();
        this.maxMetadataDepth = properties.getMaxMetadataDepth();
        this.maxMetadataNodes = properties.getMaxMetadataNodes();
    }

    Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            return sanitizeNode(tree, 0, new NodeBudget(maxMetadataNodes));
        } catch (RuntimeException | StackOverflowError error) {
            return "[UNSERIALIZABLE:" + value.getClass().getSimpleName() + "]";
        }
    }

    String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        return sanitizeText(value, maxMessageLength);
    }

    String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = truncate(value, maxLength);
        sanitized = replace(PRIVATE_KEY, sanitized);
        sanitized = replace(AUTHORIZATION, sanitized);
        sanitized = replace(AUTH_SCHEME, sanitized);
        sanitized = replace(COOKIE, sanitized);
        sanitized = replace(JWT, sanitized);
        sanitized = SENSITIVE_ASSIGNMENT.matcher(sanitized).replaceAll("$1=" + REDACTED);
        sanitized = replace(EMAIL, sanitized);
        sanitized = replace(SPANISH_ID, sanitized);
        return replace(PHONE, sanitized);
    }

    private JsonNode sanitizeNode(JsonNode node, int depth, NodeBudget budget) {
        if (!budget.consume()) {
            return TextNode.valueOf(TRUNCATED);
        }
        if (node.isContainerNode() && depth >= maxMetadataDepth) {
            return TextNode.valueOf(TRUNCATED);
        }
        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSensitiveField(entry.getKey())) {
                    sanitized.set(entry.getKey(), TextNode.valueOf(REDACTED));
                } else {
                    sanitized.set(entry.getKey(), sanitizeNode(entry.getValue(), depth + 1, budget));
                }
                if (budget.exhausted() && fields.hasNext()) {
                    sanitized.put("_exceptionLoggingTruncated", true);
                    break;
                }
            }
            return sanitized;
        } else if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            for (int index = 0; index < node.size(); index++) {
                sanitized.add(sanitizeNode(node.get(index), depth + 1, budget));
                if (budget.exhausted() && index + 1 < node.size()) {
                    sanitized.add(TRUNCATED);
                    break;
                }
            }
            return sanitized;
        } else if (node.isTextual()) {
            return TextNode.valueOf(sanitizeText(node.textValue()));
        }
        return node;
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

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= TRUNCATED.length()) {
            return TRUNCATED.substring(0, maxLength);
        }
        int end = maxLength - TRUNCATED.length();
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + TRUNCATED;
    }

    private static final class NodeBudget {
        private int remaining;

        private NodeBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean consume() {
            if (remaining == 0) {
                return false;
            }
            remaining--;
            return true;
        }

        private boolean exhausted() {
            return remaining == 0;
        }
    }
}

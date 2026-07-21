package platform.exceptionloggin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties("exception-logging")
public class ExceptionLoggingProperties {
    private static final Pattern HTTP_HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private boolean enabled = true;
    private String applicationName;
    private boolean webHandlerEnabled;
    private boolean aspectEnabled = true;
    private boolean tracePropagationEnabled = true;
    private boolean acceptIncomingTraceIds;
    private String traceHeaderName = "X-Trace-Id";
    private String correlationHeaderName = "X-Correlation-Id";
    private boolean includeStacktrace;
    private int maxMessageLength = 4096;
    private int maxStackTraceLength = 32768;
    private int maxMetadataDepth = 8;
    private int maxMetadataNodes = 1000;
    private Set<String> tracePropagationAllowedHosts = new LinkedHashSet<>();
    private Set<String> additionalSensitiveFields = new LinkedHashSet<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public boolean isWebHandlerEnabled() { return webHandlerEnabled; }
    public void setWebHandlerEnabled(boolean webHandlerEnabled) { this.webHandlerEnabled = webHandlerEnabled; }
    public boolean isAspectEnabled() { return aspectEnabled; }
    public void setAspectEnabled(boolean aspectEnabled) { this.aspectEnabled = aspectEnabled; }
    public boolean isTracePropagationEnabled() { return tracePropagationEnabled; }
    public void setTracePropagationEnabled(boolean tracePropagationEnabled) {
        this.tracePropagationEnabled = tracePropagationEnabled;
    }
    public boolean isAcceptIncomingTraceIds() { return acceptIncomingTraceIds; }
    public void setAcceptIncomingTraceIds(boolean acceptIncomingTraceIds) {
        this.acceptIncomingTraceIds = acceptIncomingTraceIds;
    }
    public String getTraceHeaderName() { return traceHeaderName; }
    public void setTraceHeaderName(String traceHeaderName) {
        this.traceHeaderName = headerName(traceHeaderName, "trace-header-name");
    }
    public String getCorrelationHeaderName() { return correlationHeaderName; }
    public void setCorrelationHeaderName(String correlationHeaderName) {
        this.correlationHeaderName = headerName(correlationHeaderName, "correlation-header-name");
    }
    public boolean isIncludeStacktrace() { return includeStacktrace; }
    public void setIncludeStacktrace(boolean includeStacktrace) { this.includeStacktrace = includeStacktrace; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = positive(maxMessageLength, "max-message-length");
    }
    public int getMaxStackTraceLength() { return maxStackTraceLength; }
    public void setMaxStackTraceLength(int maxStackTraceLength) {
        this.maxStackTraceLength = positive(maxStackTraceLength, "max-stack-trace-length");
    }
    public int getMaxMetadataDepth() { return maxMetadataDepth; }
    public void setMaxMetadataDepth(int maxMetadataDepth) {
        this.maxMetadataDepth = positive(maxMetadataDepth, "max-metadata-depth");
    }
    public int getMaxMetadataNodes() { return maxMetadataNodes; }
    public void setMaxMetadataNodes(int maxMetadataNodes) {
        this.maxMetadataNodes = positive(maxMetadataNodes, "max-metadata-nodes");
    }
    public Set<String> getTracePropagationAllowedHosts() { return Set.copyOf(tracePropagationAllowedHosts); }
    public void setTracePropagationAllowedHosts(Set<String> tracePropagationAllowedHosts) {
        this.tracePropagationAllowedHosts = cleanSet(tracePropagationAllowedHosts);
    }
    public Set<String> getAdditionalSensitiveFields() { return Set.copyOf(additionalSensitiveFields); }
    public void setAdditionalSensitiveFields(Set<String> additionalSensitiveFields) {
        this.additionalSensitiveFields = cleanSet(additionalSensitiveFields);
    }

    private static int positive(int value, String property) {
        if (value < 1) {
            throw new IllegalArgumentException("exception-logging." + property + " must be greater than zero");
        }
        return value;
    }

    private static String headerName(String value, String property) {
        if (value == null || !HTTP_HEADER_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("exception-logging." + property + " must be a valid HTTP header name");
        }
        return value;
    }

    private static LinkedHashSet<String> cleanSet(Set<String> values) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(cleaned::add);
        }
        return cleaned;
    }
}

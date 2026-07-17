package com.example.platform.exceptionlogging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("exception-logging")
public class ExceptionLoggingProperties {
    private boolean enabled = true;
    private String applicationName;
    private boolean webHandlerEnabled = true;
    private boolean aspectEnabled = true;
    private boolean tracePropagationEnabled = true;
    private String traceHeaderName = "X-Trace-Id";
    private String correlationHeaderName = "X-Correlation-Id";
    private boolean includeStacktrace = true;
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
    public String getTraceHeaderName() { return traceHeaderName; }
    public void setTraceHeaderName(String traceHeaderName) { this.traceHeaderName = traceHeaderName; }
    public String getCorrelationHeaderName() { return correlationHeaderName; }
    public void setCorrelationHeaderName(String correlationHeaderName) {
        this.correlationHeaderName = correlationHeaderName;
    }
    public boolean isIncludeStacktrace() { return includeStacktrace; }
    public void setIncludeStacktrace(boolean includeStacktrace) { this.includeStacktrace = includeStacktrace; }
    public Set<String> getAdditionalSensitiveFields() { return additionalSensitiveFields; }
    public void setAdditionalSensitiveFields(Set<String> additionalSensitiveFields) {
        this.additionalSensitiveFields = additionalSensitiveFields == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(additionalSensitiveFields);
    }
}

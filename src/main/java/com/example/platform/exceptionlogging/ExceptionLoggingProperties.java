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
    private boolean captureObject = false;
    private boolean includeStacktrace = true;
    private int maxObjectLength = 4000;
    private Set<String> sensitiveFields = new LinkedHashSet<>(Set.of(
            "password", "passwd", "secret", "token", "authorization", "apiKey",
            "creditCard", "cvv", "iban", "ssn"));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public boolean isWebHandlerEnabled() { return webHandlerEnabled; }
    public void setWebHandlerEnabled(boolean webHandlerEnabled) { this.webHandlerEnabled = webHandlerEnabled; }
    public boolean isAspectEnabled() { return aspectEnabled; }
    public void setAspectEnabled(boolean aspectEnabled) { this.aspectEnabled = aspectEnabled; }
    public boolean isCaptureObject() { return captureObject; }
    public void setCaptureObject(boolean captureObject) { this.captureObject = captureObject; }
    public boolean isIncludeStacktrace() { return includeStacktrace; }
    public void setIncludeStacktrace(boolean includeStacktrace) { this.includeStacktrace = includeStacktrace; }
    public int getMaxObjectLength() { return maxObjectLength; }
    public void setMaxObjectLength(int maxObjectLength) { this.maxObjectLength = maxObjectLength; }
    public Set<String> getSensitiveFields() { return sensitiveFields; }
    public void setSensitiveFields(Set<String> sensitiveFields) { this.sensitiveFields = sensitiveFields; }
}

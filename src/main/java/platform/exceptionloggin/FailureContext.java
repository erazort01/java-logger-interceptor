package platform.exceptionloggin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FailureContext {
    private final String table;
    private final String operation;
    private final Object failedObject;
    private final Map<String, Object> metadata;

    private FailureContext(Builder builder) {
        this.table = builder.table;
        this.operation = builder.operation;
        this.failedObject = builder.failedObject;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FailureContext empty() {
        return builder().build();
    }

    public String table() { return table; }
    public String operation() { return operation; }
    public Object failedObject() { return failedObject; }
    public Map<String, Object> metadata() { return metadata; }

    public static final class Builder {
        private String table;
        private String operation;
        private Object failedObject;
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder table(String table) { this.table = table; return this; }
        public Builder operation(String operation) { this.operation = operation; return this; }
        public Builder failedObject(Object failedObject) { this.failedObject = failedObject; return this; }
        public Builder metadata(String key, Object value) { this.metadata.put(key, value); return this; }
        public FailureContext build() { return new FailureContext(this); }
    }
}


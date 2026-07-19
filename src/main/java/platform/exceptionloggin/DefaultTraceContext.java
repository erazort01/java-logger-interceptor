package platform.exceptionloggin;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public final class DefaultTraceContext implements TraceContext {
    static final String TRACE_MDC_KEY = "traceId";
    static final String CORRELATION_MDC_KEY = "correlationId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,127}");

    private final TraceIdGenerator generator;

    public DefaultTraceContext(TraceIdGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Optional<String> currentTraceId() {
        return Optional.ofNullable(MDC.get(TRACE_MDC_KEY)).filter(DefaultTraceContext::isValid);
    }

    @Override
    public String newTraceId() {
        String generated = generator.generate();
        if (!isValid(generated)) {
            throw new IllegalStateException("TraceIdGenerator returned an invalid trace ID");
        }
        return generated;
    }

    @Override
    public TraceScope open() {
        return open(currentTraceId().orElseGet(this::newTraceId));
    }

    @Override
    public TraceScope open(String incomingTraceId) {
        String traceId = isValid(incomingTraceId) ? incomingTraceId : newTraceId();
        String previousTraceId = MDC.get(TRACE_MDC_KEY);
        String previousCorrelationId = MDC.get(CORRELATION_MDC_KEY);
        MDC.put(TRACE_MDC_KEY, traceId);
        MDC.put(CORRELATION_MDC_KEY, traceId);
        return new MdcTraceScope(traceId, previousTraceId, previousCorrelationId);
    }

    @Override
    public Runnable wrap(Runnable task) {
        String capturedTraceId = currentTraceId().orElseGet(this::newTraceId);
        return () -> {
            try (TraceScope ignored = open(capturedTraceId)) {
                task.run();
            }
        };
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> task) {
        String capturedTraceId = currentTraceId().orElseGet(this::newTraceId);
        return () -> {
            try (TraceScope ignored = open(capturedTraceId)) {
                return task.call();
            }
        };
    }

    static boolean isValid(String value) {
        return value != null && VALID_TRACE_ID.matcher(value).matches();
    }

    private static final class MdcTraceScope implements TraceScope {
        private final String traceId;
        private final String previousTraceId;
        private final String previousCorrelationId;
        private boolean closed;

        private MdcTraceScope(String traceId, String previousTraceId, String previousCorrelationId) {
            this.traceId = traceId;
            this.previousTraceId = previousTraceId;
            this.previousCorrelationId = previousCorrelationId;
        }

        @Override
        public String traceId() {
            return traceId;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            restore(TRACE_MDC_KEY, previousTraceId);
            restore(CORRELATION_MDC_KEY, previousCorrelationId);
            closed = true;
        }

        private static void restore(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }
}

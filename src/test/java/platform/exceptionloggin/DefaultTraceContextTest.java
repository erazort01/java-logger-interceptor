package platform.exceptionloggin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTraceContextTest {
    private final DefaultTraceContext context = new DefaultTraceContext(() -> "generated-trace-123");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAndReusesTraceIdInsideNestedScopes() {
        try (TraceScope outer = context.open()) {
            assertThat(outer.traceId()).isEqualTo("generated-trace-123");
            assertThat(MDC.get("traceId")).isEqualTo(outer.traceId());
            assertThat(MDC.get("correlationId")).isEqualTo(outer.traceId());

            try (TraceScope inner = context.open()) {
                assertThat(inner.traceId()).isEqualTo(outer.traceId());
            }

            assertThat(context.currentTraceId()).contains(outer.traceId());
        }

        assertThat(context.currentTraceId()).isEmpty();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void acceptsValidIncomingIdAndRejectsUnsafeInput() {
        try (TraceScope scope = context.open("shared-trace-123")) {
            assertThat(scope.traceId()).isEqualTo("shared-trace-123");
        }

        try (TraceScope scope = context.open("bad\nheader")) {
            assertThat(scope.traceId()).isEqualTo("generated-trace-123");
        }
    }

    @Test
    void propagatesCapturedContextToWrappedTaskAndRestoresWorkerMdc() {
        AtomicReference<String> observed = new AtomicReference<>();
        Runnable wrapped;
        try (TraceScope ignored = context.open("parent-trace-123")) {
            wrapped = context.wrap(() -> observed.set(MDC.get("traceId")));
        }

        MDC.put("traceId", "worker-trace-123");
        wrapped.run();

        assertThat(observed).hasValue("parent-trace-123");
        assertThat(MDC.get("traceId")).isEqualTo("worker-trace-123");
    }
}

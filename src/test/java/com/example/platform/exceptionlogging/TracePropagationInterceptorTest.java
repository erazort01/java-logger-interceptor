package com.example.platform.exceptionlogging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TracePropagationInterceptorTest {
    private final ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
    private final TraceContext context = new DefaultTraceContext(() -> "generated-trace-123");
    private final TracePropagationInterceptor interceptor = new TracePropagationInterceptor(context, properties);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sendsCurrentTraceIdToDownstreamService() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://service.test"));
        AtomicReference<String> propagated = new AtomicReference<>();

        try (TraceScope ignored = context.open("shared-trace-123")) {
            interceptor.intercept(request, new byte[0], (outgoing, body) -> {
                propagated.set(outgoing.getHeaders().getFirst("X-Trace-Id"));
                return new MockClientHttpResponse(new byte[0], 200);
            });
        }

        assertThat(propagated).hasValue("shared-trace-123");
        assertThat(request.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("shared-trace-123");
    }

    @Test
    void reusesExplicitValidHeaderWhenThereIsNoOpenContext() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://service.test"));
        request.getHeaders().set("X-Trace-Id", "explicit-trace-123");

        interceptor.intercept(request, new byte[0],
                (outgoing, body) -> new MockClientHttpResponse(new byte[0], 200));

        assertThat(request.getHeaders().getFirst("X-Trace-Id")).isEqualTo("explicit-trace-123");
        assertThat(request.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("explicit-trace-123");
    }
}

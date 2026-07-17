package com.example.platform.exceptionlogging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TracePropagationFilterTest {
    private final ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
    private final TraceContext context = new DefaultTraceContext(() -> "generated-trace-123");
    private final TracePropagationFilter filter = new TracePropagationFilter(context, properties);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesIncomingTraceThroughRequestAndResponseThenClearsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "incoming-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> observed.set(MDC.get("traceId"));

        filter.doFilter(request, response, chain);

        assertThat(observed).hasValue("incoming-trace-123");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("incoming-trace-123");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("incoming-trace-123");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void generatesTraceWhenRequestHasNoContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> observed.set(MDC.get("traceId")));

        assertThat(observed).hasValue("generated-trace-123");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("generated-trace-123");
    }
}

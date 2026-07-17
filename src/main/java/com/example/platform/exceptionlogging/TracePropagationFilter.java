package com.example.platform.exceptionlogging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
final class TracePropagationFilter extends OncePerRequestFilter {
    private final TraceContext traceContext;
    private final ExceptionLoggingProperties properties;

    TracePropagationFilter(TraceContext traceContext, ExceptionLoggingProperties properties) {
        this.traceContext = traceContext;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingTraceId = firstNonBlank(
                request.getHeader(properties.getTraceHeaderName()),
                request.getHeader(properties.getCorrelationHeaderName()));
        try (TraceScope scope = traceContext.open(incomingTraceId)) {
            response.setHeader(properties.getTraceHeaderName(), scope.traceId());
            response.setHeader(properties.getCorrelationHeaderName(), scope.traceId());
            filterChain.doFilter(request, response);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}

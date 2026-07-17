package com.example.platform.exceptionlogging;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public final class TracePropagationInterceptor implements ClientHttpRequestInterceptor {
    private final TraceContext traceContext;
    private final ExceptionLoggingProperties properties;

    public TracePropagationInterceptor(TraceContext traceContext, ExceptionLoggingProperties properties) {
        this.traceContext = traceContext;
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        String traceId = traceContext.currentTraceId()
                .orElseGet(() -> existingOrNew(headers));
        headers.set(properties.getTraceHeaderName(), traceId);
        headers.set(properties.getCorrelationHeaderName(), traceId);
        return execution.execute(request, body);
    }

    private String existingOrNew(HttpHeaders headers) {
        String existing = headers.getFirst(properties.getTraceHeaderName());
        return DefaultTraceContext.isValid(existing) ? existing : traceContext.newTraceId();
    }
}

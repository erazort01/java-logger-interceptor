package platform.exceptionloggin;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Set;

public final class TracePropagationInterceptor implements ClientHttpRequestInterceptor {
    private final TraceContext traceContext;
    private final String traceHeaderName;
    private final String correlationHeaderName;
    private final Set<String> allowedHosts;

    public TracePropagationInterceptor(TraceContext traceContext, ExceptionLoggingProperties properties) {
        this.traceContext = traceContext;
        this.traceHeaderName = properties.getTraceHeaderName();
        this.correlationHeaderName = properties.getCorrelationHeaderName();
        this.allowedHosts = properties.getTracePropagationAllowedHosts();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!isAllowed(request)) {
            return execution.execute(request, body);
        }
        HttpHeaders headers = request.getHeaders();
        String traceId = traceContext.currentTraceId()
                .orElseGet(() -> existingOrNew(headers));
        headers.set(traceHeaderName, traceId);
        headers.set(correlationHeaderName, traceId);
        return execution.execute(request, body);
    }

    private String existingOrNew(HttpHeaders headers) {
        String existing = headers.getFirst(traceHeaderName);
        return DefaultTraceContext.isValid(existing) ? existing : traceContext.newTraceId();
    }

    private boolean isAllowed(HttpRequest request) {
        String host = request.getURI().getHost();
        return host != null && allowedHosts.stream()
                .map(String::trim)
                .anyMatch(host::equalsIgnoreCase);
    }
}

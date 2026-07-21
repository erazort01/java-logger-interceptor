package platform.exceptionloggin;

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
    private final boolean acceptIncomingTraceIds;
    private final String traceHeaderName;
    private final String correlationHeaderName;

    TracePropagationFilter(TraceContext traceContext, ExceptionLoggingProperties properties) {
        this.traceContext = traceContext;
        this.acceptIncomingTraceIds = properties.isAcceptIncomingTraceIds();
        this.traceHeaderName = properties.getTraceHeaderName();
        this.correlationHeaderName = properties.getCorrelationHeaderName();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingTraceId = acceptIncomingTraceIds
                ? firstNonBlank(
                        request.getHeader(traceHeaderName),
                        request.getHeader(correlationHeaderName))
                : null;
        try (TraceScope scope = traceContext.open(incomingTraceId)) {
            response.setHeader(traceHeaderName, scope.traceId());
            response.setHeader(correlationHeaderName, scope.traceId());
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

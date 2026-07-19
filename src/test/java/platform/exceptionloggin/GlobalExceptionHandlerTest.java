package platform.exceptionloggin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final ExceptionLoggingProperties properties = new ExceptionLoggingProperties();
    private final TraceContext traceContext = new DefaultTraceContext(() -> "generated-trace-123");
    private final ContextSanitizer sanitizer = new ContextSanitizer(new ObjectMapper(), properties);

    @Test
    void keepsTechnicalBusinessDetailsOutOfTheHttpResponse() {
        GlobalExceptionHandler handler = handler((error, context) -> { });
        BusinessException error = new BusinessException(
                "RULE_001", "SQL failed password=super-secret for ana@example.com");

        ResponseEntity<ApiError> response;
        try (TraceScope ignored = traceContext.open()) {
            response = handler.handleBusiness(error);
        }

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(BusinessException.DEFAULT_PUBLIC_MESSAGE);
        assertThat(response.getBody().message()).doesNotContain("SQL", "super-secret", "ana@example.com");
    }

    @Test
    void preservesFrameworkClientStatusWithoutReportingItAsAServerFailure() {
        AtomicInteger reports = new AtomicInteger();
        GlobalExceptionHandler handler = handler((error, context) -> reports.incrementAndGet());

        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("La solicitud no es válida");
        assertThat(reports).hasValue(0);
    }

    private GlobalExceptionHandler handler(ExceptionReporter reporter) {
        return new GlobalExceptionHandler(
                reporter, new DefaultExceptionClassifier(), sanitizer, traceContext);
    }
}

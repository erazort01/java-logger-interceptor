package com.example.platform.exceptionlogging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void returnsSanitizedUnauthorizedResponseForAuthenticationFailure() {
        AtomicReference<Throwable> reported = new AtomicReference<>();
        ExceptionReporter reporter = (error, context) -> reported.set(error);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                reporter, new DefaultExceptionClassifier());
        BadCredentialsException error = new BadCredentialsException("password was incorrect");

        ResponseEntity<ApiError> response = handler.handleUnexpected(error);

        assertThat(reported.get()).isSameAs(error);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(response.getBody().message())
                .isEqualTo("No se ha podido autenticar la solicitud")
                .doesNotContain("password");
    }
}

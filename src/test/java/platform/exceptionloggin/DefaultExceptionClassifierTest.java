package platform.exceptionloggin;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import javax.security.auth.login.FailedLoginException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DefaultExceptionClassifierTest {
    private final DefaultExceptionClassifier classifier = new DefaultExceptionClassifier();

    @Test
    void classifiesDatabaseFailureInCauseChain() {
        RuntimeException error = new RuntimeException(new DataIntegrityViolationException("duplicate"));
        assertThat(classifier.classify(error)).isEqualTo(ErrorCategory.DATABASE);
    }

    @Test
    void classifiesBusinessFailure() {
        assertThat(classifier.classify(new BusinessException("RULE_001", "invalid")))
                .isEqualTo(ErrorCategory.BUSINESS);
    }

    @Test
    void classifiesSpringSecurityAuthenticationFailureInCauseChain() {
        RuntimeException error = new RuntimeException(new BadCredentialsException("secret detail"));

        assertThat(classifier.classify(error)).isEqualTo(ErrorCategory.AUTH);
    }

    @Test
    void classifiesJaasAuthenticationFailure() {
        assertThat(classifier.classify(new FailedLoginException("invalid credentials")))
                .isEqualTo(ErrorCategory.AUTH);
    }

    @Test
    void classifiesUnauthorizedHttpFailure() {
        assertThat(classifier.classify(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .isEqualTo(ErrorCategory.AUTH);
    }

    @Test
    void doesNotClassifyAuthorizationFailureAsAuthentication() {
        assertThat(classifier.classify(new AccessDeniedException("forbidden")))
                .isEqualTo(ErrorCategory.UNEXPECTED);
    }

    @Test
    void classifiesConnectivityFailure() {
        assertThat(classifier.classify(new ResourceAccessException("offline")))
                .isEqualTo(ErrorCategory.CONNECTIVITY);
    }

    @Test
    void fallsBackToUnexpected() {
        assertThat(classifier.classify(new IllegalStateException("boom")))
                .isEqualTo(ErrorCategory.UNEXPECTED);
    }

    @Test
    void stopsAtACyclicCauseChain() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThat(classifier.classify(first)).isEqualTo(ErrorCategory.UNEXPECTED));
    }
}

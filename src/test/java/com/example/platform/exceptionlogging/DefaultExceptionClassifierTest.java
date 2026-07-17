package com.example.platform.exceptionlogging;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExceptionClassifierTest {
    private final DefaultExceptionClassifier classifier = new DefaultExceptionClassifier();

    @Test
    void classifiesDatabaseFailureInCauseChain() {
        RuntimeException error = new RuntimeException(new DataIntegrityViolationException("duplicate"));
        assertThat(classifier.classify(error)).isEqualTo(ErrorCategory.DATABASE);
    }

    @Test
    void classifiesBusinessFailure() {
        assertThat(classifier.classify(new BusinessException("ORDER_001", "invalid")))
                .isEqualTo(ErrorCategory.BUSINESS);
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
}


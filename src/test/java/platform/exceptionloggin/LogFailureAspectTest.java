package platform.exceptionloggin;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogFailureAspectTest {
    @Test
    void rethrowsTheOriginalWhenACustomReporterFails() throws Throwable {
        IllegalStateException original = new IllegalStateException("original");
        IllegalArgumentException reportingFailure = new IllegalArgumentException("reporting failed");
        ExceptionReporter reporter = (error, context) -> { throw reportingFailure; };
        LogFailureAspect aspect = new LogFailureAspect(reporter);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.proceed()).thenThrow(original);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AnnotatedService.execute()");
        LogFailure annotation = AnnotatedService.class.getDeclaredMethod("execute")
                .getAnnotation(LogFailure.class);

        assertThatThrownBy(() -> aspect.reportFailure(joinPoint, annotation)).isSameAs(original);
        assertThat(original.getSuppressed()).containsExactly(reportingFailure);
    }

    private static final class AnnotatedService {
        @LogFailure(operation = "TEST")
        void execute() {
        }
    }
}

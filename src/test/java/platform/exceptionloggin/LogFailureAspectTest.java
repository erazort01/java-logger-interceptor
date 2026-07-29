package platform.exceptionloggin;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogFailureAspectTest {
    @Test
    void capturesAllArgumentsInDeclarationOrder() throws Throwable {
        IllegalStateException original = new IllegalStateException("original");
        AtomicReference<FailureContext> capturedContext = new AtomicReference<>();
        ExceptionReporter reporter = (error, context) -> capturedContext.set(context);
        LogFailureAspect aspect = new LogFailureAspect(reporter);
        ProceedingJoinPoint joinPoint = failingJoinPoint(original, "42", "updated");
        LogFailure annotation = AnnotatedService.class
                .getDeclaredMethod("executeWithAllArguments", String.class, String.class)
                .getAnnotation(LogFailure.class);

        assertThatThrownBy(() -> aspect.reportFailure(joinPoint, annotation)).isSameAs(original);
        assertThat(capturedContext.get().failedObject()).isEqualTo(new Object[]{"42", "updated"});
    }

    @Test
    void captureAllArgumentsTakesPrecedenceOverCaptureArgument() throws Throwable {
        IllegalStateException original = new IllegalStateException("original");
        AtomicReference<FailureContext> capturedContext = new AtomicReference<>();
        ExceptionReporter reporter = (error, context) -> capturedContext.set(context);
        LogFailureAspect aspect = new LogFailureAspect(reporter);
        ProceedingJoinPoint joinPoint = failingJoinPoint(original, "42", "updated");
        LogFailure annotation = AnnotatedService.class
                .getDeclaredMethod("executeWithBothOptions", String.class, String.class)
                .getAnnotation(LogFailure.class);

        assertThatThrownBy(() -> aspect.reportFailure(joinPoint, annotation)).isSameAs(original);
        assertThat(capturedContext.get().failedObject()).isEqualTo(new Object[]{"42", "updated"});
    }

    @Test
    void continuesCapturingASingleArgument() throws Throwable {
        IllegalStateException original = new IllegalStateException("original");
        AtomicReference<FailureContext> capturedContext = new AtomicReference<>();
        ExceptionReporter reporter = (error, context) -> capturedContext.set(context);
        LogFailureAspect aspect = new LogFailureAspect(reporter);
        ProceedingJoinPoint joinPoint = failingJoinPoint(original, "42", "updated");
        LogFailure annotation = AnnotatedService.class
                .getDeclaredMethod("executeWithOneArgument", String.class, String.class)
                .getAnnotation(LogFailure.class);

        assertThatThrownBy(() -> aspect.reportFailure(joinPoint, annotation)).isSameAs(original);
        assertThat(capturedContext.get().failedObject()).isEqualTo("updated");
    }

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

    private static ProceedingJoinPoint failingJoinPoint(Throwable error, Object... arguments) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.proceed()).thenThrow(error);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AnnotatedService.execute()");
        return joinPoint;
    }

    private static final class AnnotatedService {
        @LogFailure(operation = "TEST")
        void execute() {
        }

        @LogFailure(operation = "TEST", captureAllArguments = true)
        void executeWithAllArguments(String id, String value) {
        }

        @LogFailure(operation = "TEST", captureArgument = 0, captureAllArguments = true)
        void executeWithBothOptions(String id, String value) {
        }

        @LogFailure(operation = "TEST", captureArgument = 1)
        void executeWithOneArgument(String id, String value) {
        }
    }
}

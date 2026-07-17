package com.example.platform.exceptionlogging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
final class LogFailureAspect {
    private final ExceptionReporter reporter;

    LogFailureAspect(ExceptionReporter reporter) {
        this.reporter = reporter;
    }

    @Around("@annotation(logFailure)")
    Object reportFailure(ProceedingJoinPoint joinPoint, LogFailure logFailure) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable error) {
            Object failedObject = argument(joinPoint.getArgs(), logFailure.captureArgument());
            reporter.report(error, FailureContext.builder()
                    .table(emptyToNull(logFailure.table()))
                    .operation(emptyToNull(logFailure.operation()))
                    .failedObject(failedObject)
                    .metadata("method", joinPoint.getSignature().toShortString())
                    .build());
            throw error;
        }
    }

    private static Object argument(Object[] args, int index) {
        return index >= 0 && index < args.length ? args[index] : null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}


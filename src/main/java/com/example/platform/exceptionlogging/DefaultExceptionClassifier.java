package com.example.platform.exceptionlogging;

import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

public final class DefaultExceptionClassifier implements ExceptionClassifier {
    @Override
    public ErrorCategory classify(Throwable error) {
        if (contains(error, current -> current instanceof BusinessException)) {
            return ErrorCategory.BUSINESS;
        }
        if (contains(error, current -> current instanceof SQLException
                || hasTypeInHierarchy(current.getClass(), "org.springframework.dao.DataAccessException"))) {
            return ErrorCategory.DATABASE;
        }
        if (contains(error, current -> current instanceof RestClientException
                || current instanceof ConnectException
                || current instanceof SocketException
                || current instanceof SocketTimeoutException
                || current instanceof TimeoutException)) {
            return ErrorCategory.CONNECTIVITY;
        }
        return ErrorCategory.UNEXPECTED;
    }

    private static boolean contains(Throwable error, java.util.function.Predicate<Throwable> predicate) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (predicate.test(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTypeInHierarchy(Class<?> type, String expectedName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(expectedName)) {
                return true;
            }
        }
        return false;
    }
}

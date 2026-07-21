package com.example.platform.exceptionlogging;

import org.springframework.web.ErrorResponse;
import org.springframework.web.client.RestClientException;

import javax.security.auth.login.LoginException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

public final class DefaultExceptionClassifier implements ExceptionClassifier {
    private static final String SPRING_SECURITY_AUTHENTICATION_EXCEPTION =
            "org.springframework.security.core.AuthenticationException";
    private static final String SPRING_SECURITY_OAUTH2_AUTHENTICATION_EXCEPTION =
            "org.springframework.security.oauth2.core.OAuth2AuthenticationException";

    @Override
    public ErrorCategory classify(Throwable error) {
        if (contains(error, DefaultExceptionClassifier::isAuthenticationFailure)) {
            return ErrorCategory.AUTH;
        }
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

    private static boolean isAuthenticationFailure(Throwable error) {
        return error instanceof LoginException
                || error instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().value() == 401
                || hasTypeInHierarchy(error.getClass(), SPRING_SECURITY_AUTHENTICATION_EXCEPTION)
                || hasTypeInHierarchy(error.getClass(), SPRING_SECURITY_OAUTH2_AUTHENTICATION_EXCEPTION);
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

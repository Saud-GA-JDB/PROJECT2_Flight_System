package com.ga.maintenance.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.ga.common.web.model.request.response.ErrorResponse;
import com.ga.common.security.exception.IdentityUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import java.sql.SQLException;

@RestControllerAdvice(basePackages = "com.ga.maintenance")
public class GlobalExceptionHandler extends com.ga.common.web.exception.GlobalExceptionHandler {
    @ExceptionHandler(IdentityUnavailableException.class)
    public ResponseEntity<ErrorResponse> identityUnavailable(Exception ex, HttpServletRequest request) {
        return build("Identity is unavailable. Please retry shortly.", HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(AviationUnavailableException.class)
    public ResponseEntity<ErrorResponse> aviationUnavailable(Exception ex, HttpServletRequest request) {
        return build("Aviation is unavailable. Please retry shortly.", HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(feign.RetryableException.class)
    public ResponseEntity<ErrorResponse> dependencyUnavailable(Exception ex, HttpServletRequest request) {
        return build("A required service is unavailable. Please retry shortly.", HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authenticationFailed(Exception ex, HttpServletRequest request) {
        return build("Valid authentication is required.", HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class, PessimisticLockException.class})
    public ResponseEntity<ErrorResponse> lockConflict(Exception ex, HttpServletRequest request) {
        return build("Another operation is in progress. Refresh and retry.", HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()))
                return build("This information already exists. Please check your details.", HttpStatus.CONFLICT, request);
            cause = cause.getCause();
        }
        return handleGeneric(ex, request);
    }
}

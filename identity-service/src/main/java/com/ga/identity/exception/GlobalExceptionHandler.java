package com.ga.identity.exception;

import com.ga.common.web.model.request.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.logging.Logger;

@RestControllerAdvice(basePackages = "com.ga.identity")
public class GlobalExceptionHandler extends com.ga.common.web.exception.GlobalExceptionHandler {
    private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class, PessimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleLockConflict(Exception ex, HttpServletRequest request) {
        logger.warning("Request rolled back because a database lock could not be acquired");
        return build("Another operation is in progress. Refresh and retry.", HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof SQLException sqlException &&
                    "23505".equals(sqlException.getSQLState())) {
                logger.warning("Request rejected because the information already exists");
                return build("This information already exists. Please check your details.",
                        HttpStatus.CONFLICT, request);
            }
            cause = cause.getCause();
        }

        return handleGeneric(ex, request);
    }

    @ExceptionHandler(LoginLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleLoginLimit(
            LoginLimitExceededException ex, HttpServletRequest request) {
        return build(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return build(
                "Error: Email or password is incorrect, or the account is inactive.",
                HttpStatus.UNAUTHORIZED,
                request
        );
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleMail(
            MailException ex, HttpServletRequest request) {
        logger.severe("Could not send the email. Exception type: " + ex.getClass().getSimpleName());
        return build(
                "Could not send the email.",
                HttpStatus.INTERNAL_SERVER_ERROR,
                request
        );
    }

}

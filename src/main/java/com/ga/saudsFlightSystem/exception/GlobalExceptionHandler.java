package com.ga.saudsFlightSystem.exception;

import com.ga.saudsFlightSystem.model.request.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.logging.Logger;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(InformationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            InformationNotFoundException ex, HttpServletRequest request) {
        logger.warning("Request failed because the requested resource was not found");
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(InvalidInformationException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            InvalidInformationException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the supplied information was invalid");
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InformationExistException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            InformationExistException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the information already exists");
        return build(ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(IllegalEndpoint.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            IllegalEndpoint ex, HttpServletRequest request) {
        logger.warning("Request rejected because the user was not allowed to perform this action");
        return build(ex.getMessage(), HttpStatus.FORBIDDEN, request);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        logger.severe("An unexpected error occurred");
        return build("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> build(
            String message, HttpStatus status, HttpServletRequest request) {
        String errorCode = status.name();
        if (status == HttpStatus.NOT_FOUND) {
            errorCode = "RESOURCE_NOT_FOUND";
        }

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                errorCode,
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(errorResponse);
    }
}

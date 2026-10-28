package com.ga.saudsFlightSystem.exception;

import com.ga.saudsFlightSystem.model.request.response.ErrorResponse;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the request body could not be read");
        return build("Request body is missing or contains invalid JSON or field values.",
                HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InformationExistException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            InformationExistException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the information already exists");
        return build(ex.getMessage(), HttpStatus.CONFLICT, request);
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

    @ExceptionHandler(IllegalEndpoint.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            IllegalEndpoint ex, HttpServletRequest request) {
        logger.warning("Request rejected because the user was not allowed to perform this action");
        return build(ex.getMessage(), HttpStatus.FORBIDDEN, request);
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

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ErrorResponse> handleStripe(
            StripeException ex, HttpServletRequest request) {
        logger.warning("Stripe request failed. Exception type: "
                + ex.getClass().getSimpleName());

        if ("idempotency_key_in_use".equals(ex.getCode()))
            return build(
                    "Checkout is already being prepared. Please retry shortly.",
                    HttpStatus.CONFLICT,
                    request
            );

        if (ex instanceof ApiConnectionException || ex instanceof RateLimitException)
            return build(
                    "The payment provider is temporarily unavailable. Please retry shortly.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    request
            );

        return build(
                "Could not complete the checkout request.",
                HttpStatus.BAD_GATEWAY,
                request
        );
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidParameters(
            Exception ex, HttpServletRequest request) {
        logger.warning("Request rejected because the parameters were missing or invalid");
        return build("Please provide all required parameters with valid values.", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleUnknownRoute(
            Exception ex, HttpServletRequest request) {
        logger.warning("Request failed because the endpoint was not found");
        return build("This endpoint does not exist.", HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleLargeUpload(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the upload exceeded the size limit");
        return build("The uploaded file exceeds the allowed size limit.", HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleWrongMethod(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the HTTP method is not supported");
        ResponseEntity<ErrorResponse> response = build("This HTTP method is not allowed for this endpoint.", HttpStatus.METHOD_NOT_ALLOWED, request);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedContentType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        logger.warning("Request rejected because the content type is not supported");
        ResponseEntity<ErrorResponse> response = build("Please use a supported content type for this endpoint.", HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(ex.getHeaders())
                .body(response.getBody());
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

package com.ga.common.web.exception;

import com.ga.common.web.model.request.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.logging.Logger;

public abstract class GlobalExceptionHandler {
    private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(InformationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(InformationNotFoundException ex, HttpServletRequest request) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(InvalidInformationException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(InvalidInformationException ex, HttpServletRequest request) {
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(IllegalEndpoint.class)
    public ResponseEntity<ErrorResponse> handleForbidden(IllegalEndpoint ex, HttpServletRequest request) {
        return build(ex.getMessage(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler({InformationExistException.class, BusinessConflictException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build("Request body is missing or contains invalid JSON or field values.", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidParameters(Exception ex, HttpServletRequest request) {
        return build("Please provide all required parameters with valid values.", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleUnknownRoute(Exception ex, HttpServletRequest request) {
        return build("This endpoint does not exist.", HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleLargeUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build("The uploaded file exceeds the allowed size limit.", HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleWrongMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity<ErrorResponse> response = build("This HTTP method is not allowed for this endpoint.", HttpStatus.METHOD_NOT_ALLOWED, request);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedContentType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity<ErrorResponse> response = build("Please use a supported content type for this endpoint.", HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .headers(ex.getHeaders())
                .body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        logger.severe("An unexpected error occurred");
        return build("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    protected ResponseEntity<ErrorResponse> build(String message, HttpStatus status, HttpServletRequest request) {
        String errorCode = status == HttpStatus.NOT_FOUND ? "RESOURCE_NOT_FOUND" : status.name();

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

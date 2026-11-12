package com.ga.notification.exception;

import com.ga.common.web.model.request.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.ga.notification")
public class GlobalExceptionHandler extends com.ga.common.web.exception.GlobalExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> authenticationFailed(Exception ex, HttpServletRequest request) {
        return build("Valid authentication is required.", HttpStatus.UNAUTHORIZED, request);
    }
}

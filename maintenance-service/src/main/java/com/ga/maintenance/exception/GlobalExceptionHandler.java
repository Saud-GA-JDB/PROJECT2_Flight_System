package com.ga.maintenance.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.ga.maintenance")
public class GlobalExceptionHandler extends com.ga.common.web.exception.GlobalExceptionHandler {
}

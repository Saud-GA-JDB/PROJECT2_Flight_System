package com.ga.saudsFlightSystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInformationException extends RuntimeException {
    public InvalidInformationException(String msg) {
        super(msg);
    }
}

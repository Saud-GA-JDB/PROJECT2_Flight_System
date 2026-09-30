package com.ga.saudsFlightSystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class IllegalEndpoint extends RuntimeException{
    public IllegalEndpoint(String msg) {super(msg);}
}

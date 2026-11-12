package com.ga.maintenance.exception;

public class AviationUnavailableException extends RuntimeException {
    public AviationUnavailableException() {
        super("Aviation is unavailable. Please retry shortly.");
    }
}

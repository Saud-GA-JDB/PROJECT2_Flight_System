package com.ga.common.security.exception;

public class IdentityUnavailableException extends RuntimeException {
    public IdentityUnavailableException() {
        super("Identity is unavailable. Please retry shortly.");
    }
}

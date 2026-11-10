package com.ga.saudsFlightSystem.identity;

public class IdentityUnavailableException extends RuntimeException {
    public IdentityUnavailableException() {
        super("Identity is unavailable. Please retry shortly.");
    }
}

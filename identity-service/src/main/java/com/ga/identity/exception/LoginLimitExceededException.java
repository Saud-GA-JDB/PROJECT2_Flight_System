package com.ga.identity.exception;

import org.springframework.security.core.AuthenticationException;

public class LoginLimitExceededException extends AuthenticationException {
    public LoginLimitExceededException() {
        super("Too many failed login attempts. Try again after midnight Bahrain time.");
    }
}

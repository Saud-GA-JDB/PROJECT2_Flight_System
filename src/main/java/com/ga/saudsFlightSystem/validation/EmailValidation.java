package com.ga.saudsFlightSystem.validation;

import org.apache.commons.validator.routines.EmailValidator;

public class EmailValidation {
    private EmailValidation() {}

    public static boolean isValid(String email) {
        return EmailValidator.getInstance().isValid(email);
    }
}

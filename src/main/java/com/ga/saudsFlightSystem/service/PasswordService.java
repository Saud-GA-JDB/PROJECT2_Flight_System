package com.ga.saudsFlightSystem.service;

import org.passay.*;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final PasswordValidator passwordValidator = new PasswordValidator(
            new LengthRule(8, 30),
            new CharacterRule(EnglishCharacterData.UpperCase, 1),
            new CharacterRule(EnglishCharacterData.Digit, 1),
            new WhitespaceRule()
    );

    public boolean isValidPassword(String password) {
        RuleResult result = passwordValidator.validate(new PasswordData(password));
        return result.isValid();
    }
}

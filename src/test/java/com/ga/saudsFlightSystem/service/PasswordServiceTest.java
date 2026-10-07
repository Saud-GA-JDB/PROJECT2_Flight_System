package com.ga.saudsFlightSystem.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

public class PasswordServiceTest {
    PasswordService passwordService;

    @Before
    public void setUp() {
        passwordService = new PasswordService();
    }

    @Test
    @DisplayName("when valid password is used then return true")
    public final void whenValidPasswordIsUsedThenReturnTrue() {
        Assert.assertTrue(passwordService.isValidPassword("TestPassword1"));
    }

    @Test
    @DisplayName("when weak password is used then return false")
    public final void whenWeakPasswordIsUsedThenReturnFalse() {
        Assert.assertFalse(passwordService.isValidPassword("short"));
        Assert.assertFalse(passwordService.isValidPassword("testpassword1"));
        Assert.assertFalse(passwordService.isValidPassword("TestPassword"));
        Assert.assertFalse(passwordService.isValidPassword("Test Password1"));
    }
}

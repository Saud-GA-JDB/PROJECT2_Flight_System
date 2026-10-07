package com.ga.saudsFlightSystem.security;

import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.repository.UserRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class AuthenticationTest {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Before
    public void setUp() {
        User user = userRepository.findUserByEmailAddress("flighttest.customer@mailsac.com");
        Assert.assertNotNull("Add the test account from TESTING-NOTES-TEMP.md", user);
        Assert.assertTrue(user.isActive());
        Assert.assertEquals(User.Status.ACTIVE, user.getStatus());
    }

    @Test
    @DisplayName("when correct password is used then user is authenticated")
    public final void whenCorrectPasswordIsUsedThenUserIsAuthenticated() {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.customer@mailsac.com", "TestPassword1"));

        Assert.assertTrue(authentication.isAuthenticated());
        Assert.assertEquals("flighttest.customer@mailsac.com", authentication.getName());
    }

    @Test(expected = BadCredentialsException.class)
    @DisplayName("when wrong password is used then exception is thrown")
    public final void whenWrongPasswordIsUsedThenExceptionIsThrown() {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.customer@mailsac.com", "WrongPassword1"));
    }
}

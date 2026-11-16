package com.ga.identity.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtRequestFilterTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final MyUserDetailsService users = mock(MyUserDetailsService.class);
    private final JWTUtils jwt = new JWTUtils();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private String token(String key, long expiry) {
        return Jwts.builder().setSubject("test@example.com").setExpiration(new Date(expiry))
                .signWith(SignatureAlgorithm.HS256, key).compact();
    }

    private void request(String token) throws Exception {
        ReflectionTestUtils.setField(jwt, "jwtSecret", KEY);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        new JwtRequestFilter(users, jwt).doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest(), "Authentication failures must still reach the security chain");
    }

    @Test
    void authenticatesValidSignedToken() throws Exception {
        when(users.loadUserByUsername("test@example.com")).thenReturn(
                User.withUsername("test@example.com").password("unused").authorities("ACCOUNT_ACTIVE").build());
        request(token(KEY, System.currentTimeMillis() + 60_000));
        assertEquals("test@example.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(users).loadUserByUsername("test@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "wrong-signature"})
    void rejectsInvalidTokensBeforeLoadingAccounts(String kind) throws Exception {
        String value = switch (kind) {
            case "expired" -> token(KEY, System.currentTimeMillis() - 60_000);
            case "wrong-signature" -> token(Base64.getEncoder().encodeToString("different-test-key-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)), System.currentTimeMillis() + 60_000);
            default -> "invalid-token";
        };
        request(value);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsLockedOrDisabledAccounts(boolean locked) throws Exception {
        when(users.loadUserByUsername("test@example.com")).thenReturn(
                User.withUsername("test@example.com").password("unused").authorities("ACCOUNT_ACTIVE")
                        .accountLocked(locked).disabled(!locked).build());
        request(token(KEY, System.currentTimeMillis() + 60_000));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

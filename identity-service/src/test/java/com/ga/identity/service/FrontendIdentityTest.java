package com.ga.identity.service;

import com.ga.common.contract.notification.EmailRequest;
import com.ga.common.security.CurrentActor;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.identity.client.NotificationServiceClient;
import com.ga.identity.model.User;
import com.ga.identity.model.request.response.SecurityQuestionResponse;
import com.ga.identity.repository.UserRepository;
import com.ga.identity.security.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Mocked persistence and notification delivery; no database or email is used.
class FrontendIdentityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final NotificationServiceClient notifications = mock(NotificationServiceClient.class);
    private final PasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final UserService service = new UserService(users, passwords, null, null, null, null,
            null, null, notifications, profiles, null, actors);
    private final User user = new User();

    @BeforeEach
    void setup() {
        user.setId(17L);
        user.setEmailAddress("owner@example.com");
        user.setActive(true);
        user.setStatus(User.Status.ACTIVE);
        user.setSecurityQuestion("Favourite city?");
        when(users.findByEmailAddressForUpdate("owner@example.com")).thenReturn(user);
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "CUSTOMER", "ACTIVE", true));
    }

    @Test
    void codeIsHashedAndConsumedAfterReturningQuestion() {
        service.sendRecoveryQuestionCode("owner@example.com");
        ArgumentCaptor<EmailRequest> email = ArgumentCaptor.forClass(EmailRequest.class);
        verify(notifications).sendEmail(nullable(String.class), email.capture());
        String body = email.getValue().body();
        String code = body.substring(body.indexOf(" is ") + 4, body.indexOf(". It expires"));
        assertTrue(code.matches("[0-9]{6}"));
        assertNotEquals(code, user.getRecoveryQuestionCodeHash());
        assertTrue(passwords.matches(code, user.getRecoveryQuestionCodeHash()));

        var response = service.getRecoveryQuestion("owner@example.com", code);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Favourite city?", ((SecurityQuestionResponse) response.getBody()).getSecurityQuestion());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNull(user.getRecoveryQuestionCodeHash());
        assertEquals(401, service.getRecoveryQuestion("owner@example.com", code).getStatusCode().value());
    }

    @Test
    void threeWrongCodesBlockTheCorrectCodeAndCannotResetTheCooldown() {
        user.setRecoveryQuestionCodeHash(passwords.encode("123456"));
        user.setRecoveryQuestionExpiresAt(Instant.now().plusSeconds(600));
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertEquals(401, service.getRecoveryQuestion("owner@example.com", "999999").getStatusCode().value());
            assertEquals(attempt, user.getFailedRecoveryQuestionAttempts());
        }
        assertEquals(401, service.getRecoveryQuestion("owner@example.com", "123456").getStatusCode().value());
        service.sendRecoveryQuestionCode("owner@example.com");
        assertEquals(3, user.getFailedRecoveryQuestionAttempts());
        verifyNoInteractions(notifications);
    }

    @Test
    void unknownInactiveAndCooldownAccountsHaveTheSameRequestMessage() {
        var unknown = service.sendRecoveryQuestionCode("missing@example.com");
        user.setActive(false);
        var inactive = service.sendRecoveryQuestionCode("owner@example.com");
        user.setActive(true);
        user.setRecoveryQuestionExpiresAt(Instant.now().plusSeconds(600));
        var cooldown = service.sendRecoveryQuestionCode("owner@example.com");
        assertEquals(unknown.getBody().getMessage(), inactive.getBody().getMessage());
        assertEquals(unknown.getBody().getMessage(), cooldown.getBody().getMessage());
        verifyNoInteractions(notifications);
    }

    @Test
    void expiredCodeDoesNotRevealTheQuestion() {
        user.setRecoveryQuestionCodeHash(passwords.encode("123456"));
        user.setRecoveryQuestionExpiresAt(Instant.now().minusSeconds(1));
        assertEquals(401, service.getRecoveryQuestion("owner@example.com", "123456").getStatusCode().value());
    }

    @Test
    void imageCannotBeReadByAnotherCustomer() {
        assertThrows(IllegalEndpoint.class, () -> service.getProfileImage(18L));
        verifyNoInteractions(profiles);
    }

    @Test
    void storedPathsCannotEscapeTheUploadsFolder() {
        when(profiles.findImageUrlByUserId(17L)).thenReturn(Optional.of("uploads/../../private.png"));
        assertThrows(InformationNotFoundException.class, () -> service.getProfileImage(17L));
    }

    @Test
    void missingImageReturnsNotFoundForOwnerAndFAA() {
        when(profiles.findImageUrlByUserId(17L)).thenReturn(Optional.empty());
        assertThrows(InformationNotFoundException.class, () -> service.getProfileImage(17L));
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(99L, "FAA_ADMIN", "ACTIVE", true));
        assertThrows(InformationNotFoundException.class, () -> service.getProfileImage(17L));
        verify(profiles, times(2)).findImageUrlByUserId(17L);
    }
}

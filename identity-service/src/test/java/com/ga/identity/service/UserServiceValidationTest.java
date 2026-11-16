package com.ga.identity.service;

import com.ga.common.security.CurrentActor;
import com.ga.common.validation.PasswordService;
import com.ga.identity.controller.UserController;
import com.ga.identity.model.User;
import com.ga.identity.model.request.UpdateProfileRequest;
import com.ga.identity.repository.UserRepository;
import com.ga.identity.security.CurrentActorProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserServiceValidationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final UserService service = new UserService(users, passwords, null, null, null, null,
            new PasswordService(), null, null, profiles, mock(AuditLogService.class), actors);

    @Test
    void missingNewPasswordReturnsValidationResponseWithoutUpdatingAccount() throws Exception {
        MockMvcBuilders.standaloneSetup(new UserController(service, null)).build()
                .perform(post("/auth/users/changePassword").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(422));
        verifyNoInteractions(users, actors);
    }

    @Test
    void securityAnswerMatchesRecoveryNormalizationUnderTurkishLocale() {
        User user = new User();
        user.setId(7L);
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(7L, "CUSTOMER", "ACTIVE", true));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(7L)).thenReturn(Optional.empty());
        UpdateProfileRequest request = new UpdateProfileRequest();
        ReflectionTestUtils.setField(request, "securityQuestion", "City?");
        ReflectionTestUtils.setField(request, "securityQuestionAnswer", " ISTANBUL ");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            service.updateProfile(null, request, null);
            assertTrue(passwords.matches("istanbul", user.getSecurityQuestionAnswer()));
        } finally {
            Locale.setDefault(previous);
        }
    }
}

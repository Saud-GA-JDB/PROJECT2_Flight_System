package com.ga.identity.service;

import com.ga.common.security.CurrentActor;
import com.ga.common.validation.PasswordService;
import com.ga.identity.controller.UserController;
import com.ga.identity.model.User;
import com.ga.identity.model.request.UpdateProfileRequest;
import com.ga.identity.model.request.response.PersonalProfile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserServiceValidationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final UserService service = new UserService(users, passwords, null, null, null, null,
            new PasswordService(), null, null, profiles, mock(AuditLogService.class), actors);

    @Test
    void currentUserReturnsAccountAndProfileWithoutSecrets() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setEmailAddress("customer@example.com");
        user.setRole(User.Role.CUSTOMER);
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);
        user.setPassword("stored-password");
        user.setSecurityQuestionAnswer("stored-answer");

        when(actors.getCurrentActor()).thenReturn(new CurrentActor(7L, "CUSTOMER", "ACTIVE", true));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(
                new PersonalProfile("Saud", "Ahmed", "123456789", "+973", "36001234")));

        MockMvcBuilders.standaloneSetup(new UserController(service, null)).build()
                .perform(get("/auth/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.emailAddress").value("customer@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fName").value("Saud"))
                .andExpect(jsonPath("$.lName").value("Ahmed"))
                .andExpect(jsonPath("$.phoneNumberOpeningCode").value("+973"))
                .andExpect(jsonPath("$.phoneNumber").value("36001234"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.securityQuestionAnswer").doesNotExist())
                .andExpect(jsonPath("$.cpr").doesNotExist());
    }

    @Test
    void currentUserReturnsSetupStatusWithoutCompletedProfile() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setEmailAddress("customer@example.com");
        user.setRole(User.Role.CUSTOMER);
        user.setStatus(User.Status.SETUP_REQUIRED);

        when(actors.getCurrentActor()).thenReturn(new CurrentActor(7L, "CUSTOMER", "SETUP_REQUIRED", false));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(7L)).thenReturn(Optional.empty());

        MockMvcBuilders.standaloneSetup(new UserController(service, null)).build()
                .perform(get("/auth/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.status").value("SETUP_REQUIRED"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.fName").doesNotExist());
    }

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

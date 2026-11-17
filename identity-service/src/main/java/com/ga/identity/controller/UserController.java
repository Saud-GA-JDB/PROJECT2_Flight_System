package com.ga.identity.controller;

import com.ga.identity.model.request.response.ProfileUpdateResponse;
import com.ga.identity.model.request.response.SecurityQuestionResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import com.ga.identity.model.request.response.ForgetPasswordResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import com.ga.identity.model.request.response.AccountSetupResponse;
import com.ga.identity.model.request.response.CurrentUserResponse;
import com.ga.identity.model.request.*;
import com.ga.identity.service.PendingRegistrationService;
import com.ga.identity.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/auth/users")
@AllArgsConstructor
public class UserController {
    private UserService userService;
    private PendingRegistrationService pendingRegistrationService;

    @Operation(summary = "Read the current user", description = "Authenticated, including setup-required accounts. Includes a gateway-relative imageUrl or null. Reading image bytes requires an active account.")
    @GetMapping("/me")
    public CurrentUserResponse getCurrentUser() {
        return userService.getCurrentUser();
    }

    @PostMapping("/setup")
    public AccountSetupResponse finishSetup(@RequestBody RegistrationRequest request) {
        return userService.finishSetup(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        return userService.loginUser(loginRequest);
    }

    @PostMapping("/verification")
    public ResponseEntity<?> verifyCustomer(@RequestBody VerifyEmailRequest request) {
        return pendingRegistrationService.verify(request.getEmail(), request.getCode());
    }

    @PostMapping("/register/email")
    public ResponseEntity<?> sendVerificationCode(@RequestBody RegistrationEmailRequest request) {
        return pendingRegistrationService.sendCode(request.getEmail(), request.getCpr());
    }

    @Operation(summary = "Request recovery question verification", description = "Public. Body: email. Sends a six-digit code for an active account with a question. Generic response for unknown, inactive and cooldown accounts. One request per account per ten minutes.")
    @SecurityRequirements
    @PostMapping("/recoveryQuestion/email")
    public ResponseEntity<ForgetPasswordResponse> sendRecoveryQuestionCode(@RequestBody RecoveryQuestionRequest request) {
        return userService.sendRecoveryQuestionCode(request.getEmail());
    }

    @Operation(summary = "Read the recovery question", description = "Public. Body: email and code. Three incorrect attempts per code; ten-minute expiry. Success consumes the code and returns securityQuestion. Then use the existing forgetPassword endpoint with email and securityQuestionAnswer. Returns 400 for malformed input or 401 for an unavailable code.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = SecurityQuestionResponse.class)))
    @PostMapping("/recoveryQuestion")
    public ResponseEntity<?> getRecoveryQuestion(@RequestBody RecoveryQuestionRequest request) {
        return userService.getRecoveryQuestion(request.getEmail(), request.getCode());
    }

    @PostMapping("/forgetPassword")
    public ResponseEntity<?> forgetPassword(@RequestBody ForgetPasswordRequest request) {
        return userService.forgetPassword(request.getEmail(), request.getSecurityQuestionAnswer());
    }

    @PostMapping("/changePassword")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        return userService.changePassword(request.getNewPassword());
    }

    @Operation(summary = "Read a profile image", description = "Active profile owner or FAA admin only. Returns PNG bytes with Cache-Control: no-store. Use an authenticated fetch and a browser blob URL; an img tag cannot attach the bearer token. Returns 404 when no stored file exists.")
    @GetMapping(value = "/{userId}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getProfileImage(@PathVariable Long userId) {
        return userService.getProfileImage(userId);
    }

    @Operation(summary = "Update profile", description = "Existing multipart profile rules apply. Returns JSON with message and imageUrl (gateway-relative, or null). FAA-targeted updates return the target user's image reference.")
    @PutMapping("/updateProfile")
    public ResponseEntity<ProfileUpdateResponse> updateProfile(
            @RequestParam(required = false) Long userId,
            @RequestPart("request") UpdateProfileRequest request,
            @RequestParam(required = false) MultipartFile image) {
        return userService.updateProfile(userId, request, image);
    }
}

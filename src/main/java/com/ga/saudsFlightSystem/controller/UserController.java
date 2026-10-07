package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.*;
import com.ga.saudsFlightSystem.service.PendingRegistrationService;
import com.ga.saudsFlightSystem.service.UserService;
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

    @PostMapping("/setup")
    public User finishSetup(@RequestBody RegistrationRequest request) {
        return userService.finishSetup(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        return userService.loginUser(loginRequest);
    }

    @PostMapping("/verification")
    public ResponseEntity<?> verifyCustomer(@RequestBody VerifyEmailRequest request) {
        System.out.println("called verify customer controller"); /*         TODO: DEBUGGING         */
        return pendingRegistrationService.verify(request.getEmail(), request.getCode());
    }

    @PostMapping("/register/email")
    public ResponseEntity<?> sendVerificationCode(@RequestBody RegistrationEmailRequest request) {
        return pendingRegistrationService.sendCode(request.getEmail(), request.getCpr());
    }

    @PostMapping("/forgetPassword")
    public ResponseEntity<?> forgetPassword(@RequestBody ForgetPasswordRequest request) {
        return userService.forgetPassword(request.getEmail(), request.getSecurityQuestionAnswer());
    }

    @PostMapping("/changePassword")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        return userService.changePassword(request.getNewPassword());
    }

    @PutMapping("/updateProfile")
    public ResponseEntity<?> updateProfile(
            @RequestParam(required = false) Long userId,
            @RequestPart("request") UpdateProfileRequest request,
            @RequestParam(required = false) MultipartFile image) {
        return userService.updateProfile(userId, request, image);
    }
}

package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.LoginRequest;
import com.ga.saudsFlightSystem.model.request.RegistrationEmailRequest;
import com.ga.saudsFlightSystem.model.request.RegistrationRequest;
import com.ga.saudsFlightSystem.model.request.VerifyEmailRequest;
import com.ga.saudsFlightSystem.service.PendingRegistrationService;
import com.ga.saudsFlightSystem.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        System.out.println("called login controller"); /*         TODO: DEBUGGING         */

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
}

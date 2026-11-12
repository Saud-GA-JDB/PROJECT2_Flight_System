package com.ga.notification.controller;

import com.ga.common.web.exception.InvalidInformationException;
import com.ga.notification.model.request.EmailRequest;
import com.ga.notification.service.EmailService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/emails")
public class InternalEmailController {
    private final EmailService emailService;
    @Value("${notification.service-key:${NOTIFICATION_SERVICE_KEY:}}")
    private String serviceKey;

    @PostMapping
    public void send(@RequestHeader(value = "X-Service-Key", required = false) String suppliedKey, @RequestBody EmailRequest request) {
        validate(suppliedKey, request);
        emailService.sendEmail(request);
    }

    @PostMapping("/async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendAsync(@RequestHeader(value = "X-Service-Key", required = false) String suppliedKey, @RequestBody EmailRequest request) {
        validate(suppliedKey, request);
        emailService.sendAsyncEmail(request);
    }

    private void validate(String suppliedKey, EmailRequest request) {
        if (serviceKey.isBlank() || suppliedKey == null || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            throw new BadCredentialsException("A valid Notification service credential is required.");
        if (request == null || request.recipient() == null || request.recipient().isBlank()
                || request.subject() == null || request.subject().isBlank() || request.body() == null || request.body().isBlank())
            throw new InvalidInformationException("Recipient, subject, and body are required.");
        try {
            new jakarta.mail.internet.InternetAddress(request.recipient(), true).validate();
        } catch (jakarta.mail.internet.AddressException ex) {
            throw new InvalidInformationException("Provide a valid recipient email address.");
        }
    }
}

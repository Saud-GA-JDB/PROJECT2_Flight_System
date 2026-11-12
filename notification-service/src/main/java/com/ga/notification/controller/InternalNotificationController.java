package com.ga.notification.controller;

import com.ga.common.web.exception.InvalidInformationException;
import com.ga.notification.model.request.NotificationRequest;
import com.ga.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {
    private final NotificationService notificationService;
    @Value("${notification.service-key:${NOTIFICATION_SERVICE_KEY:}}")
    private String serviceKey;

    @PostMapping
    public void send(@RequestHeader(value = "X-Service-Key", required = false) String suppliedKey, @RequestBody NotificationRequest request) {
        if (serviceKey.isBlank() || suppliedKey == null || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            throw new BadCredentialsException("A valid Notification service credential is required.");
        if (request == null || request.eventName() == null || request.eventName().isBlank() || request.data() == null
                || (request.userId() == null) == (request.role() == null)
                || request.userId() != null && request.userId() <= 0
                || request.role() != null && !"FAA_ADMIN".equals(request.role()))
            throw new InvalidInformationException("Provide one positive user ID or the FAA_ADMIN role, an event name, and data.");

        if (request.userId() != null)
            notificationService.sendToUser(request.userId(), request.eventName(), request.data());
        else
            notificationService.sendToRole(request.role(), request.eventName(), request.data());
    }
}

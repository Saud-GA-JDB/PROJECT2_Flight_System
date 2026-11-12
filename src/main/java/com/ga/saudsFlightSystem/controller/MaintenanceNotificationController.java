package com.ga.saudsFlightSystem.controller;

import com.ga.common.web.exception.InvalidInformationException;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.model.request.MaintenanceCancellationNotificationRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceCancellationNotificationResponse;
import com.ga.saudsFlightSystem.notification.NotificationPublisher;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/maintenance-notifications")
public class MaintenanceNotificationController {
    private final AirplaneRepository airplanes;
    private final AirlineEmployeeRepository employees;
    private final AccountLookup accounts;
    private final NotificationPublisher notifications;
    @Value("${maintenance.notification-service-key:${MAINTENANCE_NOTIFICATION_SERVICE_KEY:}}")
    private String serviceKey;

    @PostMapping("/cancellations")
    public void cancellation(@RequestHeader(value = "X-Service-Key", required = false) String suppliedKey, @RequestBody MaintenanceCancellationNotificationRequest request) {
        if (serviceKey.isBlank() || suppliedKey == null || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            throw new BadCredentialsException("A valid Maintenance service credential is required.");
        if (request.recordId() == null || request.recordId() <= 0 || request.airplaneId() == null || request.airplaneId() <= 0
                || !"CANCELLED".equals(request.status()) || request.cancelledAt() == null || request.maintenanceUserIds() == null
                || request.maintenanceUserIds().stream().anyMatch(id -> id == null || id <= 0))
            throw new InvalidInformationException("A valid maintenance cancellation is required.");

        var airplane = airplanes.findById(request.airplaneId())
                .orElseThrow(() -> new InformationNotFoundException("Airplane not found."));
        Set<Long> recipients = new LinkedHashSet<>(accounts.findActiveUserIds(
                employees.findAdminUserIdsByAirlineId(airplane.getAirline().getId()), "AIRLINE_EMPLOYEE"));
        recipients.addAll(accounts.findActiveUserIds(request.maintenanceUserIds(), "MAINTENANCE_EMPLOYEE"));
        var payload = new MaintenanceCancellationNotificationResponse(request.recordId(), request.status(), request.cancelledAt());
        for (Long userId : recipients)
            notifications.sendToUser(userId, "Maintenance_Cancelled", payload);
    }
}

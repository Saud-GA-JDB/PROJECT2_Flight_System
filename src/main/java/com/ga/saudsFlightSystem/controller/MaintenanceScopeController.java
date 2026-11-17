package com.ga.saudsFlightSystem.controller;

import com.ga.common.contract.maintenance.MaintenanceAirlineResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import com.ga.common.contract.maintenance.MaintenanceScopeResponse;

import com.ga.saudsFlightSystem.service.MaintenanceScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/maintenance-scope")
public class MaintenanceScopeController {
    private final MaintenanceScopeService maintenanceScopeService;

    @Value("${maintenance.notification-service-key:${MAINTENANCE_NOTIFICATION_SERVICE_KEY:}}")
    private String serviceKey;

    @Hidden
    @PostMapping("/airlines")
    public List<MaintenanceAirlineResponse> getAirlines(@RequestHeader(value = "X-Service-Key", required = false) String suppliedKey,
                                                      @RequestBody List<Long> ids) {
        if (serviceKey.isBlank() || suppliedKey == null || !MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            throw new BadCredentialsException("A valid Maintenance service credential is required.");

        return maintenanceScopeService.getAirlines(ids);
    }

    @GetMapping
    public MaintenanceScopeResponse getAirlineScope() {
        return maintenanceScopeService.getAirlineScope();
    }

    @GetMapping("/airplanes/{airplaneId}")
    public MaintenanceScopeResponse getAirplaneScope(@PathVariable("airplaneId") Long airplaneId) {
        return maintenanceScopeService.getAirplaneScope(airplaneId);
    }
}
package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.MaintenanceScopeResponse;
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

    @GetMapping
    public MaintenanceScopeResponse getAirlineScope() {
        return maintenanceScopeService.getAirlineScope();
    }

    @GetMapping("/airplanes/{airplaneId}")
    public MaintenanceScopeResponse getAirplaneScope(@PathVariable("airplaneId") Long airplaneId) {
        return maintenanceScopeService.getAirplaneScope(airplaneId);
    }
}
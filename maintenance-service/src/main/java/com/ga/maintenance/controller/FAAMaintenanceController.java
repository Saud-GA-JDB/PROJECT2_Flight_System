package com.ga.maintenance.controller;

import com.ga.maintenance.model.MaintenanceRecord;
import com.ga.maintenance.model.request.*;
import com.ga.maintenance.model.request.response.*;
import com.ga.maintenance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/faaadmin")
public class FAAMaintenanceController {
    private final MaintenanceProviderService maintenanceProviderService;
    private final MaintenanceEmployeeService maintenanceEmployeeService;

    @PostMapping("/maintenanceProviders")
    public ResponseEntity<?> addMaintenanceProvider(@RequestBody AddMaintenanceProviderRequest request) {
        return maintenanceProviderService.addMaintenanceProvider(request);
    }

    @PostMapping("/maintenanceProviders/{providerId}/addMaintenanceAdmin")
    public ResponseEntity<?> addMaintenanceProviderAdmin(@PathVariable(name = "providerId") Long providerId, @RequestBody AddMaintenanceEmployeeRequest request) {
        return maintenanceEmployeeService.addMaintenanceAdmin(providerId, request);
    }
}
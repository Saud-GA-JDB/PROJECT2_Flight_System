package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.AddMaintenanceEmployeeRequest;
import com.ga.saudsFlightSystem.service.MaintenanceEmployeeService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/maintenanceAdmin")
public class MaintenanceAdminController {
    private MaintenanceEmployeeService maintenanceEmployeeService;

    @PostMapping("/technicians")
    public ResponseEntity<?> addMaintenanceTechnician(@RequestBody AddMaintenanceEmployeeRequest request) {
        return maintenanceEmployeeService.addMaintenanceTechnician(request);
    }
}

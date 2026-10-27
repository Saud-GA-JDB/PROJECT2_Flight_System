package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.AddMaintenanceEmployeeRequest;
import com.ga.saudsFlightSystem.service.MaintenanceEmployeeService;
import com.ga.saudsFlightSystem.service.MaintenanceRecordService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/maintenanceAdmin")
public class MaintenanceAdminController {
    private MaintenanceEmployeeService maintenanceEmployeeService;
    private MaintenanceRecordService maintenanceRecordService;

    @PostMapping("/technicians")
    public ResponseEntity<?> addMaintenanceTechnician(@RequestBody AddMaintenanceEmployeeRequest request) {
        return maintenanceEmployeeService.addMaintenanceTechnician(request);
    }

    @GetMapping("/maintenanceRequests")
    public ResponseEntity<?> getMaintenanceRequests() {
        return maintenanceRecordService.getMaintenanceRequests();
    }

    @PostMapping("/maintenanceRequests/{requestId}")
    public ResponseEntity<?> assignMaintenance(@PathVariable(name = "requestId") Long requestId, @RequestParam Long technicianId) {
        return maintenanceRecordService.assignService(requestId, technicianId);
    }
}

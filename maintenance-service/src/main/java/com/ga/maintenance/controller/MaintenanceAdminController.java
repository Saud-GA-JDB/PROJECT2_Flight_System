package com.ga.maintenance.controller;

import com.ga.maintenance.model.request.AddMaintenanceEmployeeRequest;
import com.ga.maintenance.model.request.CancelMaintenanceRequest;
import com.ga.maintenance.model.request.response.MaintenanceRecordResponse;
import com.ga.maintenance.service.MaintenanceEmployeeService;
import com.ga.maintenance.service.MaintenanceRecordService;
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

    @PostMapping("/maintenanceRequests/{requestId}/cancel")
    public ResponseEntity<MaintenanceRecordResponse> cancelMaintenance(@PathVariable Long requestId, @RequestBody CancelMaintenanceRequest request) {
        return maintenanceRecordService.cancelProviderMaintenance(requestId, request.getReason());
    }
}

package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.service.MaintenanceRecordService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/maintenanceTechnician")
public class MaintenanceTechnicianController {
    private MaintenanceRecordService maintenanceRecordService;

    @GetMapping("/maintenanceRecords")
    public ResponseEntity<?> getMyMaintenanceAssignments() {
        return maintenanceRecordService.getMyMaintenanceAssignments();
    }

    @PutMapping("/maintenanceRecords/{recordId}/complete")
    public ResponseEntity<?> completeMaintenance(@PathVariable(name = "recordId") Long recordId, @RequestParam(name = "findings", required = false) String findings) {
        return maintenanceRecordService.completeMaintenance(recordId, findings);
    }

}

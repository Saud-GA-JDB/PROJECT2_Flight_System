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
@RequestMapping("/airlineAdmin")
public class AirlineMaintenanceController {
    private final MaintenanceProviderService maintenanceProviderService;
    private final MaintenanceRecordService maintenanceRecordService;

    @PostMapping("/maintenanceProviders/{providerId}/contract")
    public ResponseEntity<?> contractMaintenanceProvider(@PathVariable(name = "providerId") Long providerId, @RequestHeader("Authorization") String authorization) {
        return maintenanceProviderService.contractMaintenanceProvider(providerId, authorization);
    }

    @PostMapping("/airplanes/{airplaneId}/maintenanceRequests")
    public ResponseEntity<?> addMaintenanceRecord(@PathVariable(name = "airplaneId") Long airplaneId, @RequestBody AddMaintenanceRecordRequest request, @RequestHeader("Authorization") String authorization) {
        return maintenanceRecordService.addMaintenanceRecord(airplaneId, request, authorization);
    }

    @PostMapping("/maintenanceRequests/{requestId}/cancel")
    public ResponseEntity<MaintenanceRecordResponse> cancelMaintenance(@PathVariable Long requestId, @RequestBody CancelMaintenanceRequest request, @RequestHeader("Authorization") String authorization) {
        return maintenanceRecordService.cancelAirlineMaintenance(requestId, request.getReason(), authorization);
    }

    @GetMapping("/airplanes/{airplaneId}/maintenanceRecords")
    public ResponseEntity<MaintenanceRecordListResponse> getMaintenanceHistory(@PathVariable Long airplaneId,
            @RequestParam(required = false) MaintenanceRecord.MaintenanceStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestHeader("Authorization") String authorization) {
        return maintenanceRecordService.getAirlineMaintenanceHistory(airplaneId, status, page, size, authorization);
    }
}

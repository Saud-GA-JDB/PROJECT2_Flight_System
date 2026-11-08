package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.MaintenanceRecord.MaintenanceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class MaintenanceRecordResponse {
    private Long id;
    private Long airplaneId;
    private Long providerId;
    private Long technicianId;
    private String description;
    private String findings;
    private MaintenanceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String cancellationReason;
    private Instant cancelledAt;
    private Long cancelledByUserId;
}

package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.MaintenanceRecord.MaintenanceStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class MaintenanceCancellationNotificationResponse {
    private Long recordId;
    private MaintenanceStatus status;
    private Instant cancelledAt;
}

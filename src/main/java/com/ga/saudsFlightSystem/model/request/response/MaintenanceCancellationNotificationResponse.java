package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class MaintenanceCancellationNotificationResponse {
    private Long recordId;
    private String status;
    private Instant cancelledAt;
}

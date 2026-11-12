package com.ga.saudsFlightSystem.model.request;

import java.time.Instant;
import java.util.List;

public record MaintenanceCancellationNotificationRequest(Long recordId, Long airplaneId, String status, Instant cancelledAt, List<Long> maintenanceUserIds) {}

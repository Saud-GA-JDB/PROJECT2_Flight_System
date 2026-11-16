package com.ga.common.contract.maintenance;

import java.time.Instant;
import java.util.List;

public record MaintenanceCancellationNotificationRequest(Long recordId, Long airplaneId, String status, Instant cancelledAt, List<Long> maintenanceUserIds) {}

package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;
import com.ga.saudsFlightSystem.model.ClearanceStatus;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class ClearanceNotificationResponse {
    private Long eventId;
    private Long flightId;
    private FlightOperation operation;
    private long cycle;
    private long revision;
    private ClearanceStatus status;
    private Instant occurredAt;
    private Instant expiresAt;
}

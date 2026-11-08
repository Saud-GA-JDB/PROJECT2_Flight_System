package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class ClearanceResponse {
    private FlightOperation operation;
    private String status;
    private Long facilityId;
    private long cycle;
    private long revision;
    private Instant approvedAt;
    private Instant expiresAt;
}

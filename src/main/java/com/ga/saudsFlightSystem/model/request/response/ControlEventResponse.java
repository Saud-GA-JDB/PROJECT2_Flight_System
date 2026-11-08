package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;
import com.ga.saudsFlightSystem.model.FlightControlEvent;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class ControlEventResponse {
    private Long id;
    private Long flightId;
    private Long facilityId;
    private Long actorUserId;
    private FlightOperation operation;
    private FlightControlEvent.EventType eventType;
    private long cycle;
    private long revision;
    private Instant occurredAt;
    private String reason;
}

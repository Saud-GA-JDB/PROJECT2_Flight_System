package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.Flight;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OperationResponse {
    private Long flightId;
    private String flightNumber;
    private Flight.FlightStatus status;
    private LocalDateTime actualDeparture;
    private LocalDateTime actualArrival;
    private ClearanceResponse clearance;
    private Long eventId;
}

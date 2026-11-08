package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.Flight;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class FlightOperationSummary {
    private Long flightId;
    private String flightNumber;
    private Long airlineId;
    private Long airplaneId;
    private Long originAirportId;
    private Long destinationAirportId;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private LocalDateTime actualDeparture;
    private LocalDateTime actualArrival;
    private Flight.FlightStatus status;
    private ClearanceResponse clearance;
    private List<OperationCrewResponse> crew;
    private OperationInspectionResponse latestInspection;
}

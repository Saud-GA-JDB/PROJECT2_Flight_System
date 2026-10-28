package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.FlightInspection.InspectionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FlightInspectionResponse {
    private Long id;
    private Long flightId;
    private Long airportId;
    private Long inspectorId;
    private InspectionStatus status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

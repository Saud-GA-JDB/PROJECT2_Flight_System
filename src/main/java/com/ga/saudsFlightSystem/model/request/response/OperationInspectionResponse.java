package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightInspection;

@Getter
@AllArgsConstructor
public class OperationInspectionResponse {
    private Long id;
    private FlightInspection.InspectionStatus status;
}

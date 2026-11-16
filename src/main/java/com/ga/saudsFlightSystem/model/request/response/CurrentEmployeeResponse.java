package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CurrentEmployeeResponse {
    private Long employeeId;
    private String employeeRole;
    private Long airlineId;
    private Long airportId;
    private Long operatorId;
    private Long facilityId;
    private Long providerId;
}

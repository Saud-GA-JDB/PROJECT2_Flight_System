package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.AirportEmployee.AirportRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AirportEmployeeResponse {
    private Long id;
    private Long userId;
    private Long operatorId;
    private Long airportId;
    private String fName;
    private String lName;
    private String emailAddress;
    private AirportRole airportRole;
}

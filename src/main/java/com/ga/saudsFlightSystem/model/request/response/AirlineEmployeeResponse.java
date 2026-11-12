package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.AirlineEmployee.AirlineRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class AirlineEmployeeResponse {
    private Long id;
    private Long userId;
    private Long airlineId;
    private String fName;
    private String lName;
    private String emailAddress;
    private AirlineRole airlineRole;
    private LocalDate hireDate;
    private Long salary;
}

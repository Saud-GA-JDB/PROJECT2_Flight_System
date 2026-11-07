package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.ATCEmployee.ATCRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ATCEmployeeResponse {
    private Long id;
    private Long userId;
    private Long facilityId;
    private String employeeNumber;
    private String fName;
    private String lName;
    private String emailAddress;
    private ATCRole atcRole;
}

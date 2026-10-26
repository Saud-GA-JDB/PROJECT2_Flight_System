package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.MaintenanceEmployee.MaintenanceRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MaintenanceEmployeeResponse {
    private Long id;
    private Long userId;
    private Long providerId;
    private String fName;
    private String lName;
    private String emailAddress;
    private MaintenanceRole maintenanceRole;
    private String licenseNumber;
}

package com.ga.maintenance.model.request.response;

import com.ga.maintenance.model.MaintenanceEmployee.MaintenanceRole;
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

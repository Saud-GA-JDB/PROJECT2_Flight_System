package com.ga.maintenance.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CurrentEmployeeResponse {
    private Long employeeId;
    private String employeeRole;
    private Long providerId;
}

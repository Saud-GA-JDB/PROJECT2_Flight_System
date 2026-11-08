package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.Duty;

@Getter
@AllArgsConstructor
public class OperationCrewResponse {
    private Long employeeId;
    private Duty duty;
}

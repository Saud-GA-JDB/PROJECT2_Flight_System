package com.ga.saudsFlightSystem.model.request;

import com.ga.saudsFlightSystem.model.Duty;
import lombok.Getter;

@Getter
public class AddCrewAssignmentRequest {
    private Long employeeId;
    private Duty duty;
}

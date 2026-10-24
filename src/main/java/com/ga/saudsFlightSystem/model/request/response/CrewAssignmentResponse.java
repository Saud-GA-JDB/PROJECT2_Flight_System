package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Duty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CrewAssignmentResponse {
    private Long id;
    private Long flightId;
    private Long employeeId;
    private Duty duty;
    private LocalDateTime assignedAt;
}

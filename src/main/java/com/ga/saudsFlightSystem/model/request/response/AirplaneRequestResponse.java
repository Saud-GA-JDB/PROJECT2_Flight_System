package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AirplaneRequestResponse {
    private Long requestId;
    private Long airplaneId;
    private String registrationNumber;
    private String model;
    private AirplaneRequest.ApprovalStatus status;
    private String reviewReason;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
}

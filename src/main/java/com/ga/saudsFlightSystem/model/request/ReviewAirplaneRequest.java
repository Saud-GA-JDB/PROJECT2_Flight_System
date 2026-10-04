package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class ReviewAirplaneRequest {
    private AirplaneRequest.ApprovalStatus status;
    private String reviewReason;
}

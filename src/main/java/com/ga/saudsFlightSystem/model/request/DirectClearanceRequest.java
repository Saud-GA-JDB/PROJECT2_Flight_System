package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;

@Getter
public class DirectClearanceRequest {
    private FlightOperation operation;
    private Long expectedRevision;
    private String reason;
}

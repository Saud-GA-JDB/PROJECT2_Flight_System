package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;
import com.ga.saudsFlightSystem.model.ClearanceStatus;

@Getter
public class DecideClearanceRequest {
    private FlightOperation operation;
    private ClearanceStatus decision;
    private Long expectedRevision;
    private String reason;
}

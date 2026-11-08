package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;

@Getter
public class WithdrawClearanceRequest {
    private FlightOperation operation;
    private Long expectedRevision;
}

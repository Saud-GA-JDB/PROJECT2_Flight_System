package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.ga.saudsFlightSystem.model.FlightOperation;
import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReadinessResponse {
    private Long flightId;
    private FlightOperation operation;
    private Instant evaluatedAt;
    private boolean readyForRequest;
    private boolean readyForApproval;
    private boolean readyForAction;
    private long clearanceRevision;
    private List<ReadinessBlocker> blockers;
}

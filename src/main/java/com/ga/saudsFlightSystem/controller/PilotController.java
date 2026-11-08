package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.FlightOperation;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.model.request.*;
import com.ga.saudsFlightSystem.service.FlightOperationService;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@AllArgsConstructor
@RequestMapping("/pilot")
public class PilotController {
    private FlightOperationService operationService;

    @GetMapping("/flights")
    public ResponseEntity<FlightOperationListResponse> getFlights(
            @RequestParam(required = false) Flight.FlightStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operationService.getPilotFlights(status, fromDate, toDate, page, size);
    }

    @GetMapping("/flights/{flightId}")
    public ResponseEntity<FlightOperationSummary> getFlight(@PathVariable Long flightId) {
        return operationService.getPilotFlight(flightId);
    }

    @GetMapping("/flights/{flightId}/readiness")
    public ResponseEntity<ReadinessResponse> getReadiness(@PathVariable Long flightId) {
        return operationService.getPilotReadiness(flightId);
    }

    @GetMapping("/flights/{flightId}/controlEvents")
    public ResponseEntity<ControlEventListResponse> getEvents(@PathVariable Long flightId,
            @RequestParam(required = false) FlightOperation operation,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operationService.getPilotEvents(flightId, operation, page, size);
    }

    @PostMapping("/flights/{flightId}/requestTakeOff")
    public ResponseEntity<OperationResponse> requestTakeOff(@PathVariable Long flightId, @RequestBody RequestClearanceRequest request) {
        return operationService.requestClearance(flightId, FlightOperation.DEPARTURE, request.getExpectedRevision());
    }

    @PostMapping("/flights/{flightId}/requestLanding")
    public ResponseEntity<OperationResponse> requestLanding(@PathVariable Long flightId, @RequestBody RequestClearanceRequest request) {
        return operationService.requestClearance(flightId, FlightOperation.ARRIVAL, request.getExpectedRevision());
    }

    @PostMapping("/flights/{flightId}/clearance/withdraw")
    public ResponseEntity<OperationResponse> withdrawClearance(@PathVariable Long flightId, @RequestBody WithdrawClearanceRequest request) {
        return operationService.withdrawClearance(flightId, request.getOperation(), request.getExpectedRevision());
    }
}

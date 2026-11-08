package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.FlightOperation;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.model.request.*;
import com.ga.saudsFlightSystem.service.FlightOperationService;
import com.ga.saudsFlightSystem.service.ATCService;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@AllArgsConstructor
@RequestMapping("/atc")
public class ATCController {
    private ATCService atcService;
    private FlightOperationService operationService;

    @GetMapping("/facility")
    public ResponseEntity<FacilityResponse> getOwnFacility() {
        return atcService.getOwnFacility();
    }
    @GetMapping("/flights")
    public ResponseEntity<FlightOperationListResponse> getFlights(
            @RequestParam(required = false) FlightOperation operation,
            @RequestParam(required = false) Flight.FlightStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operationService.getTowerFlights(operation, status, fromDate, toDate, page, size);
    }

    @GetMapping("/flights/{flightId}")
    public ResponseEntity<FlightOperationSummary> getFlight(@PathVariable Long flightId) {
        return operationService.getTowerFlight(flightId);
    }

    @GetMapping("/flights/{flightId}/readiness")
    public ResponseEntity<ReadinessResponse> getReadiness(@PathVariable Long flightId, @RequestParam FlightOperation operation) {
        return operationService.getTowerReadiness(flightId, operation);
    }

    @GetMapping("/requests")
    public ResponseEntity<FlightOperationListResponse> getRequests(@RequestParam(required = false) FlightOperation operation,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operationService.getTowerRequests(operation, page, size);
    }

    @GetMapping("/flights/{flightId}/controlEvents")
    public ResponseEntity<ControlEventListResponse> getEvents(@PathVariable Long flightId,
            @RequestParam(required = false) FlightOperation operation,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operationService.getTowerEvents(flightId, operation, page, size);
    }

    @PostMapping("/flights/{flightId}/clearance/decision")
    public ResponseEntity<OperationResponse> decideClearance(@PathVariable Long flightId, @RequestBody DecideClearanceRequest request) {
        return operationService.decideClearance(flightId, request.getOperation(), request.getExpectedRevision(), request.getDecision(), request.getReason());
    }

    @PostMapping("/flights/{flightId}/clearance/direct")
    public ResponseEntity<OperationResponse> directClearance(@PathVariable Long flightId, @RequestBody DirectClearanceRequest request) {
        return operationService.directClearance(flightId, request.getOperation(), request.getExpectedRevision(), request.getReason());
    }

    @PostMapping("/flights/{flightId}/clearance/revoke")
    public ResponseEntity<OperationResponse> revokeClearance(@PathVariable Long flightId, @RequestBody RevokeClearanceRequest request) {
        return operationService.revokeClearance(flightId, request.getOperation(), request.getExpectedRevision(), request.getReason());
    }
}

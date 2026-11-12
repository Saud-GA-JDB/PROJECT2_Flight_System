package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.FlightOperation;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest;
import com.ga.saudsFlightSystem.model.request.AddFlightRequest;
import com.ga.saudsFlightSystem.model.request.AddAirplaneRequest;
import com.ga.saudsFlightSystem.service.*;
import com.sun.tools.javac.Main;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping(path = "/airlineAdmin")
public class AirlineAdminController {
    private AirlineEmployeeService airlineEmployeeService;
    private AirplaneService airplaneService;
    private CrewAssignmentService crewAssignmentService;
    private FlightService flightService;
    private FlightInspectionService flightInspectionService;
    private FlightOperationService operationService;

    @PostMapping("/airplanes")
    public ResponseEntity<?> addAirplane(@RequestBody AddAirplaneRequest request) {
        return airplaneService.addAirplane(request.getRegistrationNumber(), request.getModel(), request.getStandardSeatCapacity(), request.getFirstClassSeatCapacity(), request.getMaxMileage());
    }

    @PostMapping("airplanes/{airplaneId}/requestActivation")
    public ResponseEntity<?> requestActivation(@PathVariable(name = "airplaneId") Long airplaneId) {
        return airplaneService.requestActivation(airplaneId);
    }

    @PostMapping("airplanes/{airplaneId}/addFlight")
    public ResponseEntity<?> addFlight(@PathVariable(name = "airplaneId") Long id, @RequestBody AddFlightRequest request) {
        return airlineEmployeeService.addFlight(id, request);
    }

    @PostMapping("flights/{flightId}/addCrewAssignment")
    public ResponseEntity<?> addCrewAssignment(@PathVariable(name = "flightId") Long flightId, @RequestBody AddCrewAssignmentRequest request) {
        return crewAssignmentService.addCrewAssignment(flightId, request);
    }

    @GetMapping("flights/{flightId}/crewAssignments")
    public ResponseEntity<?> getCrewAssignments(@PathVariable(name = "flightId") Long flightId) {
        return crewAssignmentService.getCrewAssignments(flightId);
    }

    @DeleteMapping("flights/{flightId}/crewAssignments/{employeeId}")
    public ResponseEntity<?> removeCrewAssignment(@PathVariable(name = "flightId") Long flightId, @PathVariable(name = "employeeId") Long employeeId) {
        return crewAssignmentService.removeCrewAssignment(flightId, employeeId);
    }

    @PutMapping("flights/{flightId}/activate")
    public ResponseEntity<?> activateFlight(@PathVariable(name = "flightId") Long flightId) {
        return flightService.activateFlight(flightId);
    }





    @PostMapping("/flights/{flightId}/reinspection")
    public ResponseEntity<?> requestReinspection(@PathVariable(name = "flightId") Long flightId) {
        return flightInspectionService.requestReInspection(flightId);
    }

    @GetMapping("/flights/{flightId}/inspections")
    public ResponseEntity<?> getFlightInspections(@PathVariable(name = "flightId") Long flightId) {
        return flightInspectionService.getFlightInspections(flightId);
    }

    @GetMapping("/flights/{flightId}/operations")
    public ResponseEntity<FlightOperationSummary> getOperations(@PathVariable Long flightId) {
        return operationService.getAirlineFlight(flightId);
    }

    @GetMapping("/flights/{flightId}/controlEvents")
    public ResponseEntity<ControlEventListResponse> getControlEvents(@PathVariable Long flightId,
            @RequestParam(required = false) FlightOperation operation,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operationService.getAirlineEvents(flightId, operation, page, size);
    }




}

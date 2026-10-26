package com.ga.saudsFlightSystem.controller;

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
    AirlineEmployeeService airlineEmployeeService;
    AirplaneService airplaneService;
    CrewAssignmentService crewAssignmentService;
    FlightService flightService;
    private MaintenanceProviderService maintenanceProviderService;

    @PostMapping("/airplanes") // Adds the airplane; activation requires FAA approval.
    public ResponseEntity<?> addAirplane(@RequestBody AddAirplaneRequest request) {
        return airplaneService.addAirplane(request.getRegistrationNumber(), request.getModel(), request.getStandardSeatCapacity(), request.getFirstClassSeatCapacity(), request.getMaxMileage());
    }

    @PostMapping("airplanes/{airplaneId}/requestActivation")
    public ResponseEntity<?> requestActivation(@PathVariable(name = "airplaneId") Long airplaneId) {
        return airplaneService.requestActivation(airplaneId);
    }

    @PostMapping("airplanes/{airplaneId}/addFlight")
    public ResponseEntity<?> addFlight(@PathVariable(name = "airplaneId") Long id, @RequestBody AddFlightRequest request) {
        return airlineEmployeeService.addFlight(id, request); //TODO: SHOULDN'T it be in flight service?
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

    @PostMapping("/maintenanceProviders/{providerId}/contract")
    public ResponseEntity<?> contractMaintenanceProvider(@PathVariable(name = "providerId") Long providerId) {
        return maintenanceProviderService.contractMaintenanceProvider(providerId);
    }
}

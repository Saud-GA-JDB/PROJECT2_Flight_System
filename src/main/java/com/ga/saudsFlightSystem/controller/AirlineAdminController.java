package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest;
import com.ga.saudsFlightSystem.model.request.AddFlightRequest;
import com.ga.saudsFlightSystem.model.request.AddAirplaneRequest;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceRecordRequest;
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
    private MaintenanceRecordService maintenanceRecordService;
    private MaintenanceProviderService maintenanceProviderService;
    private FlightInspectionService flightInspectionService;

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

    @PostMapping("/maintenanceProviders/{providerId}/contract")
    public ResponseEntity<?> contractMaintenanceProvider(@PathVariable(name = "providerId") Long providerId) {
        return maintenanceProviderService.contractMaintenanceProvider(providerId);
    }

    @PostMapping("/airplanes/{airplaneId}/maintenanceRequests")
    public ResponseEntity<?> addMaintenanceRecord(@PathVariable(name = "airplaneId") Long airplaneId, @RequestBody AddMaintenanceRecordRequest request) {
        return maintenanceRecordService.addMaintenanceRecord(airplaneId, request);
    }

    @PostMapping("/flights/{flightId}/reinspection")
    public ResponseEntity<?> requestReinspection(@PathVariable(name = "flightId") Long flightId) {
        return flightInspectionService.requestReInspection(flightId);
    }

    @GetMapping("/flights/{flightId}/inspections")
    public ResponseEntity<?> getFlightInspections(@PathVariable(name = "flightId") Long flightId) {
        return flightInspectionService.getFlightInspections(flightId);
    }

}

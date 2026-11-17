package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.AirportEmployeeResponse;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.request.response.AirlineFlightListResponse;
import com.ga.saudsFlightSystem.service.FlightService;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;

import com.ga.saudsFlightSystem.model.AirportEmployee;
import com.ga.saudsFlightSystem.model.request.AddAirportEmployeeRequest;
import com.ga.saudsFlightSystem.service.AirportEmployeeService;
import com.ga.saudsFlightSystem.service.FlightInspectionService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/airportAdmin")
public class AirportAdminController {

    private AirportEmployeeService airportEmployeeService;
    private FlightInspectionService flightInspectionService;
    private FlightService flightService;

    @GetMapping("/flights")
    public ResponseEntity<AirlineFlightListResponse> getFlights(@RequestParam(required = false) Flight.FlightStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(flightService.getAirportFlights(status, fromDate, toDate, page, size));
    }

    @GetMapping("/employees")
    public ResponseEntity<List<AirportEmployeeResponse>> getEmployees(@RequestParam(required = false) AirportEmployee.AirportRole role) {
        return ResponseEntity.ok(airportEmployeeService.getAirportEmployees(role));
    }

    @PostMapping("/employees")
    public ResponseEntity<?> addAirportEmployee(@RequestParam(name = "role") AirportEmployee.AirportRole role, @RequestBody AddAirportEmployeeRequest request) {
        return airportEmployeeService.addAirportEmployee(role, request);
    }

    @PostMapping("/flights/{flightId}/inspection")
    public ResponseEntity<?> assignInspector(@PathVariable(name = "flightId") Long flightId, @RequestParam(name = "inspectorId") Long inspectorId) {
        return flightInspectionService.assignInspector(flightId, inspectorId);
    }

    @GetMapping("/inspections")
    public ResponseEntity<?> getAirportInspections() {
        return flightInspectionService.getAirportInspections();
    }

}

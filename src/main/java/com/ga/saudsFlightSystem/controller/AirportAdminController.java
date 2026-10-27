package com.ga.saudsFlightSystem.controller;

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

    @PostMapping("/employees")
    public ResponseEntity<?> addAirportEmployee(@RequestParam(name = "role") AirportEmployee.AirportRole role, @RequestBody AddAirportEmployeeRequest request) {
        return airportEmployeeService.addAirportEmployee(role, request);
    }

    @PostMapping("/flights/{flightId}/inspection")
    public ResponseEntity<?> assignInspector(@PathVariable(name = "flightId") Long flightId, @RequestParam(name = "inspectorId") Long inspectorId) {
        return flightInspectionService.assignInspector(flightId, inspectorId);
    }
}

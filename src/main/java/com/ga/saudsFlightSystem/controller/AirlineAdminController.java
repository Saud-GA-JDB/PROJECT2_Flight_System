package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.AddFlightRequest;
import com.ga.saudsFlightSystem.model.request.AddAirplaneRequest;
import com.ga.saudsFlightSystem.service.AirlineEmployeeService;
import com.ga.saudsFlightSystem.service.AirplaneService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping(path = "/airlineAdmin")
public class AirlineAdminController {
    AirlineEmployeeService airlineEmployeeService;
    AirplaneService airplaneService;

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
}

package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.AddAirplaneRequest;
import com.ga.saudsFlightSystem.service.AirlineService;
import com.ga.saudsFlightSystem.service.AirplaneService;
import lombok.AllArgsConstructor;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping(path = "/airline")
public class AirlineController {
    private AirlineService airlineService;
    private AirplaneService airplaneService;

    @PostMapping("/airplanes") //this is for adding the airplane not activating it
    public ResponseEntity<?> addAirplane(@RequestBody AddAirplaneRequest addAirplaneRequest) {
        return airplaneService.addAirplane(addAirplaneRequest.getRegistrationNumber(), addAirplaneRequest.getModel(), addAirplaneRequest.getStandardSeatCapacity(), addAirplaneRequest.getFirstClassSeatCapacity(), addAirplaneRequest.getMaxMileage());
    }
}

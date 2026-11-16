package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.AirportResponse;
import com.ga.saudsFlightSystem.service.AirportService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/airports")
public class AirportController {
    private AirportService airportService;

    @GetMapping
    public List<AirportResponse> getAirports() {
        return airportService.getAirports();
    }
}

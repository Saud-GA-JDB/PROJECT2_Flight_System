package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.FlightSearchResponse;
import com.ga.saudsFlightSystem.service.FlightService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/flights")
public class FlightController {
    private FlightService flightService;

    // TODO: maybe use request DTO with @ModelAttribute for the search parameters? search later about it
    @GetMapping("/search")
    public FlightSearchResponse searchFlights(@RequestParam(required = false) String date, @RequestParam(required = false) String airlineCode,
                                              @RequestParam(required = false) String originAirport, @RequestParam(required = false) String destinationAirport,
                                              @RequestParam(required = false) String originCity, @RequestParam(required = false) String destinationCity,
                                              @RequestParam(required = false) String originCountry, @RequestParam(required = false) String destinationCountry,
                                              @RequestParam(required = false) String seatType, @RequestParam(defaultValue = "1") String page,
                                              @RequestParam(defaultValue = "10") String size, @RequestParam(defaultValue = "scheduledDeparture,asc") String sort) {
        return flightService.searchFlights(date, airlineCode, originAirport, destinationAirport,
                originCity, destinationCity, originCountry, destinationCountry, seatType, page, size, sort);
    }
}

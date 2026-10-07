package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class FlightResponse {
    private Long id;
    private String flightNumber;
    private String airlineCode;
    private String originAirport;
    private String destinationAirport;
    private String originCity;
    private String destinationCity;
    private String originCountry;
    private String destinationCountry;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private int firstClassSeatsCount;
    private int standardSeatsCount;
}

package com.ga.saudsFlightSystem.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AddFlightRequest {
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private String originAirportIataCode;
    private String arrivalAirportIataCode;

}

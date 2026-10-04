package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Flight;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AddFlightResponse {
    private String flightNumber;
    private String airplaneRegistrationNumber;
    private String originAirportIataCode;
    private String arrivalAirportIataCode;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private Flight.FlightStatus status;
}

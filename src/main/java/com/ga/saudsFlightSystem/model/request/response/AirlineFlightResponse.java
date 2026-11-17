package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Flight.FlightStatus;
import com.ga.saudsFlightSystem.model.Currency;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AirlineFlightResponse {
    private Long id;
    private String flightNumber;
    private Long airlineId;
    private Long airplaneId;
    private String airplaneRegistrationNumber;
    private Long originAirportId;
    private String originAirport;
    private Long destinationAirportId;
    private String destinationAirport;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private LocalDateTime actualDeparture;
    private LocalDateTime actualArrival;
    private FlightStatus status;
    private int firstClassSeatsCount;
    private int standardSeatsCount;
    private Long firstClassPrice;
    private Long standardPrice;
    private Currency currency;
}

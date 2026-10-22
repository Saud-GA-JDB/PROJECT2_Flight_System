package com.ga.saudsFlightSystem.model.request;

import com.ga.saudsFlightSystem.model.Currency;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AddFlightRequest {
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private String originAirportIataCode;
    private String arrivalAirportIataCode;
    private Long firstClassPrice;
    private Long standardPrice;
    private Currency currency = Currency.BHD;

}

package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FlightDetailResponse {
    private FlightResponse flight;
    private int firstClassSeatsCapacity;
    private int standardSeatsCapacity;
    private List<Long> availableFirstClassSeats;
    private List<Long> availableStandardSeats;
}

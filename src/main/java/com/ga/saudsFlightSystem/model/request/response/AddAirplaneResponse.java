package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Airplane;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

//@AllArgsConstructor
@Getter
@AllArgsConstructor
public class AddAirplaneResponse {
    private String registrationNumber;
    private String model;
    private int seatCapacity;
    private Long maxMileage;
    private LocalDateTime addedAt;
    private String addedBy;
    private Airplane.Status status;
}

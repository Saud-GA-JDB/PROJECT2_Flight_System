package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class AddAirplaneRequest {
    private String registrationNumber;
    private String model;
    private int seatCapacity;
    private Long maxMileage;
}

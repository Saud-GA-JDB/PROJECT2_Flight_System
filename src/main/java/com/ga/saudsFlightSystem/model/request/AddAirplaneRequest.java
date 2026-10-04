package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class AddAirplaneRequest {
    private String registrationNumber;
    private String model;
    private int firstClassSeatCapacity;
    private int standardSeatCapacity;
    private Long maxMileage;
}

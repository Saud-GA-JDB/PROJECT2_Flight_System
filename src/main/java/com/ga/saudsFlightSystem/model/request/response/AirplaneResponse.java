package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Airplane;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AirplaneResponse {
    private Long id;
    private String registrationNumber;
    private String model;
    private int firstClassSeatsCapacity;
    private int standardSeatsCapacity;
    private Long maxMileage;
    private LocalDateTime addedAt;
    private Airplane.Status status;
}

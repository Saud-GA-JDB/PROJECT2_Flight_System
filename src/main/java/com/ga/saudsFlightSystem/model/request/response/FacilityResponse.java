package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.ControlFacility.FacilityType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FacilityResponse {
    private Long id;
    private Long providerId;
    private Long airportId;
    private String name;
    private FacilityType facilityType;
}

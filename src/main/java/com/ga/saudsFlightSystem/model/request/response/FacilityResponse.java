package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.ControlFacility.FacilityType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class FacilityResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long providerId;
    private Long airportId;
    private String name;
    private FacilityType facilityType;
}

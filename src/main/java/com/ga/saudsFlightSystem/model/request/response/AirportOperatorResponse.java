package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class AirportOperatorResponse {
    private Long id;
    private String name;
    private String contactEmail;
}

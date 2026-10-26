package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MaintenanceProviderResponse {
    private Long id;
    private String name;
    private String contactEmail;
}

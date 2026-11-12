package com.ga.maintenance.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MaintenanceProviderResponse {
    private Long id;
    private String name;
    private String contactEmail;
}

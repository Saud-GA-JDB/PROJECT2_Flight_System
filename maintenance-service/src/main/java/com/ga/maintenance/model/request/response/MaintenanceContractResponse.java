package com.ga.maintenance.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MaintenanceContractResponse {
    private Long id;
    private Long airlineId;
    private String airlineName;
    private String airlineCode;
    private Long providerId;
    private String providerName;
    private String providerContactEmail;
}

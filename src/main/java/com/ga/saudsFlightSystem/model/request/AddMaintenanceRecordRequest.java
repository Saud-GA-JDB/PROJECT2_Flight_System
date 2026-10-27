package com.ga.saudsFlightSystem.model.request;

import lombok.Getter;

@Getter
public class AddMaintenanceRecordRequest {
    private Long providerId;
    private String description;
}

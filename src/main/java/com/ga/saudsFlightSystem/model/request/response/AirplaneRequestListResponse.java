package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class AirplaneRequestListResponse {
    private List<AirplaneRequestResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;
    private boolean canRequestActivation;
    private LocalDateTime nextEligibleRequestAt;
}

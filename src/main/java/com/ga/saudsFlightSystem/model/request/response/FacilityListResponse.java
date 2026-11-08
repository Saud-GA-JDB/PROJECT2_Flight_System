package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FacilityListResponse {
    private List<FacilityResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;
}

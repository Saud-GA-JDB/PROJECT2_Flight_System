package com.ga.saudsFlightSystem.model.request.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AirlineFlightListResponse {
    private List<AirlineFlightResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;
}

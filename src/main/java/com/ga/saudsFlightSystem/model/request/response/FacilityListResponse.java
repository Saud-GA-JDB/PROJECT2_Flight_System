package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
@AllArgsConstructor
public class FacilityListResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<FacilityResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;
}

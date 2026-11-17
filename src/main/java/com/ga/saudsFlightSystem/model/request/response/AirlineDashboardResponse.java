package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class AirlineDashboardResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String timeZone;
    private String revenueDateBasis;
    private boolean refundsSupported;
    private List<AirlineDashboardDayResponse> days;
    private List<AirlineRevenueResponse> revenue;
}

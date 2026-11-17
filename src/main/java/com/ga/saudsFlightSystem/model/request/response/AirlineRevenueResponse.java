package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class AirlineRevenueResponse {
    private LocalDate date;
    private Currency currency;
    private long paidTotal;
    private Long refundedTotal;
}

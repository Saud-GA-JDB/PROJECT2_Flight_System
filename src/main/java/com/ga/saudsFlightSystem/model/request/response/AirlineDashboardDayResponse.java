package com.ga.saudsFlightSystem.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class AirlineDashboardDayResponse {
    private LocalDate date;
    private long flightCount;
    private long bookingCount;
    private long bookedBookings;
    private long cancelledBookings;
    private long finishedBookings;
    private long occupiedSeats;
    private long seatCapacity;
    private BigDecimal occupancyPercentage;
}

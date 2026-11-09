package com.ga.saudsFlightSystem.model.request.response;

import java.time.LocalDateTime;

public record BookingSummaryResponse(
        Long id,
        LocalDateTime bookedAt,
        LocalDateTime updatedAt,
        String bookingRef,
        String seatNumber,
        String status
) {}

package com.ga.saudsFlightSystem.model.request.response;

import java.time.LocalDateTime;

public record BookingSummaryResponse(
        Long id,
        LocalDateTime bookedAt,
        LocalDateTime updatedAt,
        String bookingRef,
        String seatNumber,
        String status,
        Long userId,
        Long flightId,
        String flightNumber,
        String airlineCode,
        String originAirport,
        String destinationAirport,
        LocalDateTime scheduledDeparture,
        LocalDateTime scheduledArrival,
        LocalDateTime actualDeparture,
        LocalDateTime actualArrival,
        String flightStatus
) {}

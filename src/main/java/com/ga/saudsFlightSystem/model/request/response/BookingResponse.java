package com.ga.saudsFlightSystem.model.request.response;

import com.ga.saudsFlightSystem.model.Booking;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private String bookingRef;
    private Long userId;
    private Long flightId;
    private String seatNumber;
    private Booking.BookingStatus status;
    private LocalDateTime bookedAt;
}

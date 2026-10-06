package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.service.BookingService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/bookings")
public class BookingController {
    private BookingService bookingService;

    @GetMapping
    public List<Booking> getBookings() {
        return bookingService.getBookings();
    }

    @DeleteMapping("{bookingId}")
    public ResponseEntity<?> cancelBooking(@PathVariable(name = "bookingId") Long bookingId) {
        return bookingService.cancelBooking(bookingId);
    }

    @GetMapping("/search")
    public List<Booking> searchBookings(@RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) Long flightId,
                                        @RequestParam(required = false) Booking.BookingStatus status) {
        return bookingService.getUserBookings(userId, flightId, status);
    }
}

package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.service.BookingService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/filter")
    public List<Booking> searchBookings(@RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) Long flightId,
                                        @RequestParam(required = false) Booking.BookingStatus status) {
        return bookingService.getUserBookings(userId, flightId, status);
    }
}

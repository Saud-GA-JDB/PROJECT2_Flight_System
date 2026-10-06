package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.service.BookingService;
import com.ga.saudsFlightSystem.model.request.response.BookingResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/customer")
public class CustomerController {
    private BookingService bookingService;

    @PostMapping("{userId}/flights/{flightId}/{seatType}/{seatId}/book")
    public ResponseEntity<BookingResponse> bookFlight (@PathVariable(name = "userId") Long userId, @PathVariable(name = "flightId") Long flightId,@PathVariable(name = "seatType") String seatType, @PathVariable(name = "seatId") Long seatId) {
        return bookingService.bookFlight(userId, flightId, seatType, seatId);
    }
}

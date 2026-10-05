package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository <Booking, Long> {
    boolean existsByFlightIdAndSeatNumberAndStatus(Long flightId, String seatNumber, Booking.BookingStatus status);
}

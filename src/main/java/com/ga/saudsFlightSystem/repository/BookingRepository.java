package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository <Booking, Long> {
    boolean existsByFlightIdAndSeatNumberAndStatus(Long flightId, String seatNumber, Booking.BookingStatus status);
    List<Booking> findByFlight_Airline_IdOrderByBookedAtDesc(Long airlineId);

}

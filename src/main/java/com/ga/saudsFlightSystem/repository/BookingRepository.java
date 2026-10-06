package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository <Booking, Long> {
    boolean existsByFlightIdAndSeatNumberAndStatus(Long flightId, String seatNumber, Booking.BookingStatus status);
    List<Booking> findByFlight_Airline_IdOrderByBookedAtDesc(Long airlineId);
    List<Booking> findByUser_IdAndFlight_Airline_IdOrderByBookedAtDesc(Long userId, Long airlineId);

    @Query("""
    SELECT b FROM Booking b
    WHERE b.flight.airline.id = :airlineId
      AND (:userId IS NULL OR b.user.id = :userId)
      AND (:flightId IS NULL OR b.flight.id = :flightId)
      AND (:status IS NULL OR b.status = :status)
    ORDER BY b.bookedAt DESC
    """)
    List<Booking> searchBookings(
            @Param("airlineId") Long airlineId,
            @Param("userId") Long userId,
            @Param("flightId") Long flightId,
            @Param("status") Booking.BookingStatus status
    );

}

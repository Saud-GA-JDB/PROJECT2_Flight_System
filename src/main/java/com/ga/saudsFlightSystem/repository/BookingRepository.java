package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository <Booking, Long> {
    @Query("""
        SELECT b FROM Booking b
        LEFT JOIN FETCH b.flight f
        LEFT JOIN FETCH f.airline
        LEFT JOIN FETCH f.originAirport
        LEFT JOIN FETCH f.destinationAirport
        WHERE b.userId = :userId
        """)
    List<Booking> findByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT b FROM Booking b
        LEFT JOIN FETCH b.flight f
        LEFT JOIN FETCH f.airline
        LEFT JOIN FETCH f.originAirport
        LEFT JOIN FETCH f.destinationAirport
        WHERE b.id = :bookingId
        """)
    Optional<Booking> findByIdWithFlight(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findByIdForPayment(@Param("bookingId") Long bookingId);

    boolean existsByFlightIdAndSeatNumberAndStatus(Long flightId, String seatNumber, Booking.BookingStatus status);

    @Query("""
        SELECT b.seatNumber FROM Booking b
        WHERE b.flight.id = :flightId AND b.status = :status
        """)
    List<String> findSeatNumbersByFlightIdAndStatus(
            @Param("flightId") Long flightId,
            @Param("status") Booking.BookingStatus status);

    @Query("""
        SELECT b FROM Booking b
        LEFT JOIN FETCH b.flight f
        LEFT JOIN FETCH f.airline
        LEFT JOIN FETCH f.originAirport
        LEFT JOIN FETCH f.destinationAirport
        WHERE f.airline.id = :airlineId
        ORDER BY b.bookedAt DESC
        """)
    List<Booking> findByFlight_Airline_IdOrderByBookedAtDesc(@Param("airlineId") Long airlineId);
    @Query("select b.flight.id from Booking b where b.id = :bookingId")
    Optional<Long> findFlightIdByBookingId(@Param("bookingId") Long bookingId);

    @Query("""
        SELECT b FROM Booking b
        LEFT JOIN FETCH b.flight f
        LEFT JOIN FETCH f.airline
        LEFT JOIN FETCH f.originAirport
        LEFT JOIN FETCH f.destinationAirport
        WHERE f.airline.id = :airlineId
          AND (:userId IS NULL OR b.userId = :userId)
          AND (:flightId IS NULL OR f.id = :flightId)
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

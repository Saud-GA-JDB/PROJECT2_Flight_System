package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    interface AirlineRevenue {
        LocalDate getDate();
        String getCurrency();
        long getPaidTotal();
    }

    @Query(value = """
        SELECT CAST(f.scheduled_departure AS date) AS "date",
               p.currency AS "currency", SUM(p.amount) AS "paidTotal"
        FROM payments p
        JOIN bookings b ON b.id = p.booking_id
        JOIN flights f ON f.id = b.flight_id
        WHERE f.airline_id = :airlineId
          AND f.scheduled_departure >= :fromDate
          AND f.scheduled_departure < :toDate
          AND p.status = 'PAID'
        GROUP BY CAST(f.scheduled_departure AS date), p.currency
        ORDER BY CAST(f.scheduled_departure AS date), p.currency
        """, nativeQuery = true)
    List<AirlineRevenue> findAirlineRevenue(@Param("airlineId") Long airlineId,
            @Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query("select p.booking.id from Payment p where p.id = :paymentId")
    Optional<Long> findBookingIdByPaymentId(@Param("paymentId") Long paymentId);

    Optional<Payment> findByBooking_Id(Long bookingId);
}

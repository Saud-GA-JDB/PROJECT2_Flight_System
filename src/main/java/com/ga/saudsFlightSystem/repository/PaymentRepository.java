package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select p.booking.id from Payment p where p.id = :paymentId")
    Optional<Long> findBookingIdByPaymentId(@Param("paymentId") Long paymentId);

    Optional<Payment> findByBooking_Id(Long bookingId);
    Optional<Payment> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
}

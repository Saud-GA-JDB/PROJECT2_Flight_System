package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.Payment;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.response.PaymentResponse;
import com.ga.saudsFlightSystem.model.request.response.CheckoutResponse;
import com.stripe.model.checkout.Session;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.PaymentRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@AllArgsConstructor
public class PaymentService {
    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long bookingId) {
        User user = UserService.getCurrentLoggedInUser();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No booking with that id found"));

        if (!Objects.equals(user.getId(), booking.getUser().getId()))
            throw new IllegalEndpoint("You can only access your own payment");

        Payment payment = paymentRepository.findByBooking_Id(bookingId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No payment found for this booking"));

        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus());
    }

    @Transactional
    public void updatePaymentFromStripe(Session session) {
        if (session.getMetadata() == null ||
                !session.getMetadata().containsKey("paymentId"))
            return;

        Long paymentId;

        try {
            paymentId = Long.valueOf(session.getMetadata().get("paymentId"));
        } catch (NumberFormatException ex) {
            throw new InvalidInformationException("Invalid payment reference");
        }

        Long bookingId = paymentRepository.findBookingIdByPaymentId(paymentId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No payment with that id found"));

        bookingRepository.findByIdForPayment(bookingId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No booking with that id found"));

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No payment with that id found"));

        if (!Boolean.FALSE.equals(session.getLivemode()) ||
                !"payment".equals(session.getMode()) ||
                !Objects.equals(session.getClientReferenceId(), paymentId.toString()) ||
                !Objects.equals(session.getAmountTotal(), payment.getAmount()) ||
                !payment.getCurrency().name().equalsIgnoreCase(session.getCurrency()))
            throw new InvalidInformationException("Checkout session does not match this payment");

        if (payment.getStripeCheckoutSessionId() != null &&
                !payment.getStripeCheckoutSessionId().equals(session.getId()))
            throw new InformationExistException("This payment already has another checkout session");

        payment.setStripeCheckoutSessionId(session.getId());

        if ("paid".equals(session.getPaymentStatus())) {
            payment.setStatus(Payment.PaymentStatus.PAID);
            payment.setStripePaymentIntentId(session.getPaymentIntent());
        } else if ("expired".equals(session.getStatus()) &&
                payment.getStatus() != Payment.PaymentStatus.PAID) {
            payment.setStatus(Payment.PaymentStatus.EXPIRED);
        }

        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public String getCheckoutSessionId(Long paymentId) {
        User user = UserService.getCurrentLoggedInUser();

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No payment with that id found"));

        if (!Objects.equals(user.getId(), payment.getBooking().getUser().getId()))
            throw new IllegalEndpoint("You can only access your own payment");

        return payment.getStripeCheckoutSessionId();
    }

    @Transactional
    public CheckoutResponse saveCheckoutSession(Long bookingId, Session session) {
        User user = UserService.getCurrentLoggedInUser();

        Booking booking = bookingRepository.findByIdForPayment(bookingId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No booking with that id found"));

        if (!Objects.equals(user.getId(), booking.getUser().getId()))
            throw new IllegalEndpoint("You can only pay for your own booking");

        Payment payment = paymentRepository.findByBooking_Id(bookingId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No payment found for this booking"));

        if (!Objects.equals(session.getClientReferenceId(), payment.getId().toString()) ||
                !Objects.equals(session.getAmountTotal(), payment.getAmount()) ||
                !payment.getCurrency().name().equalsIgnoreCase(session.getCurrency()) ||
                !Boolean.FALSE.equals(session.getLivemode()))
            throw new InvalidInformationException("Checkout session does not match this payment");

        if (payment.getStripeCheckoutSessionId() != null &&
                !payment.getStripeCheckoutSessionId().equals(session.getId()))
            throw new InformationExistException("This payment already has another checkout session");

        payment.setStripeCheckoutSessionId(session.getId());
        paymentRepository.save(payment);

        if (payment.getStatus() != Payment.PaymentStatus.PENDING ||
                booking.getStatus() != Booking.BookingStatus.BOOKED ||
                !"open".equals(session.getStatus()))
            return new CheckoutResponse(payment.getId(), null);

        return new CheckoutResponse(payment.getId(), session.getUrl());
    }

    @Transactional
    public PaymentResponse preparePayment(Long bookingId) {
        User user = UserService.getCurrentLoggedInUser();

        Booking booking = bookingRepository.findByIdForPayment(bookingId)
                .orElseThrow(() ->
                        new InformationNotFoundException("No booking with that id found"));

        if (!Objects.equals(user.getId(), booking.getUser().getId()))
            throw new IllegalEndpoint("You can only pay for your own booking");

        if (booking.getStatus() != Booking.BookingStatus.BOOKED)
            throw new InvalidInformationException("You can only pay for an active booking");

        Flight flight = booking.getFlight();

        if (flight.getStatus() != Flight.FlightStatus.ACTIVE)
            throw new InvalidInformationException("This flight is not open for payment");

        if (!flight.getScheduledDeparture().isAfter(LocalDateTime.now().plusMinutes(5)))
            throw new InvalidInformationException("Payment closes five minutes before departure");

        Payment existingPayment = paymentRepository.findByBooking_Id(bookingId)
                .orElse(null);

        if (existingPayment != null) {
            if (existingPayment.getStatus() == Payment.PaymentStatus.PAID)
                throw new InvalidInformationException("This booking is already paid");
            if (existingPayment.getStatus() == Payment.PaymentStatus.EXPIRED)
                throw new InvalidInformationException("This payment has expired");
            if (existingPayment.getStripeCheckoutSessionId() == null &&
                    (existingPayment.getCreatedAt() == null ||
                            !existingPayment.getCreatedAt()
                                    .isAfter(LocalDateTime.now().minusHours(23))))
                throw new InformationExistException(
                        "This payment needs verification before checkout can be retried");
            return new PaymentResponse(
                    existingPayment.getId(),
                    existingPayment.getAmount(),
                    existingPayment.getCurrency(),
                    existingPayment.getStatus());
        }

        String seatNumber = booking.getSeatNumber();
        Long amount;

        if (seatNumber != null && seatNumber.startsWith("firstClass-"))
            amount = flight.getFirstClassPrice();
        else if (seatNumber != null && seatNumber.startsWith("standard-"))
            amount = flight.getStandardPrice();
        else
            throw new InvalidInformationException("This booking has an invalid seat class");

        if (amount == null || amount <= 0 || flight.getCurrency() == null)
            throw new InvalidInformationException("This flight does not have a valid price");

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(amount);
        payment.setCurrency(flight.getCurrency());
        payment.setStatus(Payment.PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);

        return new PaymentResponse(
                savedPayment.getId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getStatus());
    }
}

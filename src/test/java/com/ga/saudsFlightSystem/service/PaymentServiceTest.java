package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Booking;
import com.ga.saudsFlightSystem.model.Currency;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.Payment;
import com.ga.saudsFlightSystem.model.request.response.PaymentResponse;
import com.ga.saudsFlightSystem.repository.BookingRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import com.ga.saudsFlightSystem.repository.PaymentRepository;
import com.stripe.model.checkout.Session;
import jakarta.persistence.EntityManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
@Transactional
public class PaymentServiceTest {
    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    FlightRepository flightRepository;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    EntityManager entityManager;

    Long bookingId;
    Long flightId;
    boolean committedFixtures;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "flighttest.customer@mailsac.com",
                                "TestPassword1")));

        Booking template = bookingRepository.findById(900001L).orElse(null);
        Assert.assertNotNull(
                "Add the test booking from TESTING-NOTES-TEMP.md", template);

        Flight source = template.getFlight();
        Flight flight = new Flight();
        flight.setFlightNumber("PAYMENT-TEST-" + UUID.randomUUID());
        flight.setAirline(source.getAirline());
        flight.setAirplane(source.getAirplane());
        flight.setOriginAirport(source.getOriginAirport());
        flight.setDestinationAirport(source.getDestinationAirport());
        flight.setStatus(Flight.FlightStatus.ACTIVE);
        flight.setScheduledDeparture(LocalDateTime.now().plusDays(3));
        flight.setScheduledArrival(LocalDateTime.now().plusDays(3).plusHours(2));
        flight.setStandardPrice(15000L);
        flight.setFirstClassPrice(30000L);
        flight.setCurrency(Currency.USD);
        flightId = flightRepository.save(flight).getId();

        Booking booking = new Booking();
        booking.setUser(UserService.getCurrentLoggedInUser());
        booking.setFlight(flight);
        booking.setBookingRef(UUID.randomUUID().toString());
        booking.setSeatNumber("standard-1");
        booking.setStatus(Booking.BookingStatus.BOOKED);
        bookingId = bookingRepository.save(booking).getId();
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();

        if (committedFixtures) {
            if (!TestTransaction.isActive())
                TestTransaction.start();

            paymentRepository.findByBooking_Id(bookingId)
                    .ifPresent(paymentRepository::delete);
            bookingRepository.deleteById(bookingId);
            flightRepository.deleteById(flightId);
            TestTransaction.flagForCommit();
        }
    }

    @Test
    @DisplayName("when payment is prepared then flight pricing is copied")
    public final void whenPaymentIsPreparedThenFlightPricingIsCopied() {
        PaymentResponse response = paymentService.preparePayment(bookingId);

        Assert.assertNotNull(response.getId());
        Assert.assertEquals(Long.valueOf(15000L), response.getAmount());
        Assert.assertEquals(Currency.USD, response.getCurrency());
        Assert.assertEquals(Payment.PaymentStatus.PENDING, response.getStatus());
    }

    @Test(expected = IllegalEndpoint.class)
    @DisplayName("when another user prepares payment then access is denied")
    public final void whenAnotherUserPreparesPaymentThenAccessIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "flighttest.airline@mailsac.com",
                                "TestPassword1")));

        paymentService.preparePayment(bookingId);
    }

    @Test
    @DisplayName("when Stripe amount differs then payment is not confirmed")
    public final void whenStripeAmountDiffersThenPaymentIsNotConfirmed() {
        PaymentResponse payment = paymentService.preparePayment(bookingId);
        Session session = session(payment.getId(), "complete", "paid");
        session.setAmountTotal(1L);

        Assert.assertThrows(InvalidInformationException.class,
                () -> paymentService.updatePaymentFromStripe(session));

        Assert.assertEquals(
                Payment.PaymentStatus.PENDING,
                paymentRepository.findById(payment.getId())
                        .orElseThrow().getStatus());
    }

    @Test
    @DisplayName("when two requests prepare payment then one payment is created")
    public final void whenTwoRequestsPreparePaymentThenOnePaymentIsCreated()
            throws Exception {
        List<PaymentResponse> responses = runTogether(
                () -> paymentService.preparePayment(bookingId),
                () -> paymentService.preparePayment(bookingId));

        Assert.assertEquals(responses.get(0).getId(), responses.get(1).getId());

        Long count = entityManager.createQuery(
                        "select count(p) from Payment p where p.booking.id = :bookingId",
                        Long.class)
                .setParameter("bookingId", bookingId)
                .getSingleResult();

        Assert.assertEquals(Long.valueOf(1L), count);
    }

    @Test
    @DisplayName("when paid and expired updates race then payment remains paid")
    public final void whenPaidAndExpiredUpdatesRaceThenPaymentRemainsPaid()
            throws Exception {
        PaymentResponse payment = paymentService.preparePayment(bookingId);
        Session paid = session(payment.getId(), "complete", "paid");
        Session expired = session(payment.getId(), "expired", "unpaid");

        runTogether(
                () -> {
                    paymentService.updatePaymentFromStripe(paid);
                    return true;
                },
                () -> {
                    paymentService.updatePaymentFromStripe(expired);
                    return true;
                });

        Payment savedPayment = paymentRepository.findById(payment.getId())
                .orElseThrow();

        Assert.assertEquals(Payment.PaymentStatus.PAID, savedPayment.getStatus());
        Assert.assertEquals(
                "pi_test_" + payment.getId(),
                savedPayment.getStripePaymentIntentId());
    }

    private Session session(Long paymentId, String status, String paymentStatus) {
        Session session = new Session();
        session.setId("cs_test_" + paymentId);
        session.setClientReferenceId(paymentId.toString());
        session.setMetadata(Map.of("paymentId", paymentId.toString()));
        session.setLivemode(false);
        session.setMode("payment");
        session.setAmountTotal(15000L);
        session.setCurrency("usd");
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);

        if ("paid".equals(paymentStatus))
            session.setPaymentIntent("pi_test_" + paymentId);

        return session;
    }

    private <T> List<T> runTogether(Callable<T> first, Callable<T> second)
            throws Exception {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedFixtures = true;

        // Simulate two requests calling the service at the same time.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<T> firstResult = executor.submit(
                    authenticatedTask(first, authentication, ready, start));
            Future<T> secondResult = executor.submit(
                    authenticatedTask(second, authentication, ready, start));

            Assert.assertTrue("Workers did not start",
                    ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            return List.of(
                    firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            Assert.assertTrue("Workers did not stop",
                    executor.awaitTermination(15, TimeUnit.SECONDS));
            TestTransaction.start();
        }
    }

    private <T> Callable<T> authenticatedTask(
            Callable<T> task,
            Authentication authentication,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            SecurityContextHolder.setContext(
                    SecurityContextHolder.createEmptyContext());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            try {
                ready.countDown();

                if (!start.await(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Timed out waiting to start");

                return task.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}

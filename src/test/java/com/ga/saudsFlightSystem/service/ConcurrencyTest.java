package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddFlightRequest;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.model.request.ReviewAirplaneRequest;
import com.ga.saudsFlightSystem.repository.*;
import com.ga.saudsFlightSystem.security.MyUserDetails;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
public class ConcurrencyTest {
    @Autowired
    BookingService bookingService;
    @Autowired
    AirlineEmployeeService airlineEmployeeService;
    @Autowired
    FAAAdminService faaAdminService;
    @Autowired
    PendingRegistrationService pendingRegistrationService;
    @Autowired
    FlightRepository flightRepository;
    @Autowired
    AirplaneRepository airplaneRepository;
    @Autowired
    BookingRepository bookingRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AirportRepository airportRepository;
    @Autowired
    AirplaneRequestRepository airplaneRequestRepository;
    @Autowired
    PendingRegistrationRepository pendingRegistrationRepository;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    EntityManager entityManager;
    @MockitoBean
    EmailService emailService;
    @MockitoBean
    NotificationService notificationService;
    @MockitoBean
    AuditLogService auditLogService;
    @MockitoBean
    FlightService flightService;

    Long airplaneId;
    Long flightId;
    Long userId;
    Long pendingId;
    Authentication authentication;
    TransactionTemplate transactionTemplate;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        transactionTemplate = new TransactionTemplate(transactionManager);
        login("flighttest.customer@mailsac.com");
        userId = ((MyUserDetails) authentication.getPrincipal()).getUser().getId();

        transactionTemplate.executeWithoutResult(status -> {
            User admin = userRepository.findUserByEmailAddress("flighttest.airline@mailsac.com");
            Assert.assertNotNull("Add the seeded airline admin before running these tests", admin);
            Airplane airplane = new Airplane();
            airplane.setAirline(admin.getAirlineEmployee().getAirline());
            airplane.setRegistrationNumber("CONC-" + UUID.randomUUID());
            airplane.setModel("Concurrency Test");
            airplane.setStandardSeatsCapacity(10);
            airplane.setFirstClassSeatsCapacity(2);
            airplane.setMaxMileage(5000L);
            airplane.setStatus(Airplane.Status.ACTIVE);
            airplaneId = airplaneRepository.save(airplane).getId();

            Flight flight = new Flight();
            flight.setAirplane(airplane);
            flight.setAirline(airplane.getAirline());
            flight.setFlightNumber("CONC-" + airplaneId);
            flight.setOriginAirport(airportRepository.findByIataCode("BAH"));
            flight.setDestinationAirport(airportRepository.findByIataCode("DXB"));
            Assert.assertNotNull("Add the seeded BAH airport", flight.getOriginAirport());
            Assert.assertNotNull("Add the seeded DXB airport", flight.getDestinationAirport());
            flight.setScheduledDeparture(LocalDateTime.now().plusDays(3));
            flight.setScheduledArrival(LocalDateTime.now().plusDays(3).plusHours(2));
            flight.setStandardSeatsCount(10);
            flight.setFirstClassSeatsCount(2);
            flight.setStandardPrice(15000L);
            flight.setFirstClassPrice(30000L);
            flight.setStatus(Flight.FlightStatus.ACTIVE);
            flightId = flightRepository.save(flight).getId();
        });
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
        if (transactionTemplate == null)
            return;
        transactionTemplate.executeWithoutResult(status -> {
            if (pendingId != null)
                pendingRegistrationRepository.deleteById(pendingId);
            if (airplaneId != null) {
                entityManager.createQuery("delete from Booking b where b.flight.airplane.id = :airplaneId")
                        .setParameter("airplaneId", airplaneId).executeUpdate();
                entityManager.createQuery("delete from Flight f where f.airplane.id = :airplaneId")
                        .setParameter("airplaneId", airplaneId).executeUpdate();
                entityManager.createQuery("delete from AirplaneRequest r where r.airplane.id = :airplaneId")
                        .setParameter("airplaneId", airplaneId).executeUpdate();
                airplaneRepository.deleteById(airplaneId);
            }
        });
    }

    @Test
    @DisplayName("when two requests book the same seat then only one booking succeeds")
    public final void whenSameSeatIsBookedThenOnlyOneSucceeds() throws Exception {
        runConcurrently(() -> book(1L), () -> {
            InvalidInformationException ex = Assert.assertThrows(InvalidInformationException.class, () -> book(1L));
            Assert.assertEquals("sorry this seat is already booked", ex.getMessage());
        });
        checkBookings(1, 9);
    }

    @Test
    @DisplayName("when different seats are booked then both seat counts are deducted")
    public final void whenDifferentSeatsAreBookedThenBothAreDeducted() throws Exception {
        runConcurrently(() -> book(1L), () -> book(2L));
        checkBookings(2, 8);
    }

    @Test
    @DisplayName("when different bookings are cancelled then both seats are restored")
    public final void whenBookingsAreCancelledThenBothSeatsAreRestored() throws Exception {
        Long firstId = addBooking(1L);
        Long secondId = addBooking(2L);
        runConcurrently(() -> bookingService.cancelBooking(firstId), () -> bookingService.cancelBooking(secondId));
        checkBookings(0, 10);
        Assert.assertEquals(Booking.BookingStatus.CANCELLED, bookingRepository.findById(firstId).orElseThrow().getStatus());
        Assert.assertEquals(Booking.BookingStatus.CANCELLED, bookingRepository.findById(secondId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("when overlapping flights are created then only one succeeds")
    public final void whenFlightsOverlapThenOnlyOneSucceeds() throws Exception {
        login("flighttest.airline@mailsac.com");
        LocalDateTime departure = LocalDateTime.now().plusDays(7).withNano(0);
        AddFlightRequest request = jsonMapper.readValue(
                "{\"originAirportIataCode\":\"BAH\",\"arrivalAirportIataCode\":\"DXB\","
                        + "\"scheduledDeparture\":\"" + departure + "\","
                        + "\"scheduledArrival\":\"" + departure.plusHours(2) + "\","
                        + "\"firstClassPrice\":30000,\"standardPrice\":15000,\"currency\":\"USD\"}", AddFlightRequest.class);
        runConcurrently(() -> airlineEmployeeService.addFlight(airplaneId, request), () -> {
            InvalidInformationException ex = Assert.assertThrows(InvalidInformationException.class,
                    () -> airlineEmployeeService.addFlight(airplaneId, request));
            Assert.assertEquals("The airplane must have at least 2 hours between flights", ex.getMessage());
        });
        Assert.assertEquals(2, flightRepository.findByAirplane_Id(airplaneId).size());
    }

    @Test
    @DisplayName("when admins review the same request then only one decision succeeds")
    public final void whenRequestIsReviewedThenOnlyOneDecisionSucceeds() throws Exception {
        login("flighttest.faa@mailsac.com");
        Long requestId = transactionTemplate.execute(status -> {
            Airplane airplane = airplaneRepository.findById(airplaneId).orElseThrow();
            airplane.setStatus(Airplane.Status.GROUNDED);
            AirplaneRequest request = new AirplaneRequest();
            request.setAirplane(airplane);
            request.setRequestedBy(userRepository.findById(userId).orElseThrow());
            return airplaneRequestRepository.save(request).getId();
        });
        ReviewAirplaneRequest accepted = jsonMapper.readValue(
                "{\"status\":\"ACCEPTED\",\"reviewReason\":\"Test approval\"}", ReviewAirplaneRequest.class);
        ReviewAirplaneRequest denied = jsonMapper.readValue(
                "{\"status\":\"DENIED\",\"reviewReason\":\"Test denial\"}", ReviewAirplaneRequest.class);
        runConcurrently(() -> faaAdminService.reviewAirplaneRequest(requestId, accepted), () -> {
            InvalidInformationException ex = Assert.assertThrows(InvalidInformationException.class,
                    () -> faaAdminService.reviewAirplaneRequest(requestId, denied));
            Assert.assertEquals("This activation request has already been reviewed", ex.getMessage());
        });
        Assert.assertEquals(AirplaneRequest.ApprovalStatus.ACCEPTED,
                airplaneRequestRepository.findById(requestId).orElseThrow().getStatus());
        Assert.assertEquals(Airplane.Status.ACTIVE, airplaneRepository.findById(airplaneId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("when incorrect codes are submitted then neither failed attempt is lost")
    public final void whenCodesAreIncorrectThenBothAttemptsAreCounted() throws Exception {
        String email = "concurrency-" + UUID.randomUUID() + "@example.com";
        pendingId = transactionTemplate.execute(status -> {
            PendingRegistration pending = new PendingRegistration();
            pending.setEmailAddress(email);
            pending.setCpr(String.format("%09d", ThreadLocalRandom.current().nextInt(1000000000)));
            pending.setCodeHash(passwordEncoder.encode("123456"));
            pending.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            return pendingRegistrationRepository.save(pending).getId();
        });
        runConcurrently(() -> checkIncorrectCode(email), () -> checkIncorrectCode(email));
        Assert.assertEquals(2, pendingRegistrationRepository.findById(pendingId).orElseThrow().getFailedAttempts());
    }

    private void checkIncorrectCode(String email) {
        InvalidInformationException ex = Assert.assertThrows(InvalidInformationException.class,
                () -> pendingRegistrationService.verify(email, "000000"));
        Assert.assertEquals("Incorrect verification code.", ex.getMessage());
    }

    @Test
    @DisplayName("when writer holds lock then readers and writers wait")
    public final void whenWriterHoldsLockThenReadersAndWritersWait() throws Exception {
        checkLocks(true, false, true);
        checkLocks(true, true, true);
    }

    @Test
    @DisplayName("when reader holds lock then readers proceed and writers wait")
    public final void whenReaderHoldsLockThenReadersProceedAndWritersWait() throws Exception {
        checkLocks(false, false, false);
        checkLocks(false, true, true);
    }

    private void checkLocks(boolean firstWrites, boolean secondWrites, boolean shouldWait) throws Exception {
        runConcurrently(() -> lockFlight(firstWrites), () ->
                transactionTemplate.executeWithoutResult(status -> lockFlight(secondWrites)), shouldWait);
    }

    private void lockFlight(boolean write) {
        if (write)
            flightRepository.findByIdForCrewWrite(flightId).orElseThrow();
        else
            flightRepository.findByIdForCrewRead(flightId).orElseThrow();
    }

    private void book(Long seatId) {
        bookingService.bookFlight(userId, flightId, "standard", seatId);
    }

    private Long addBooking(Long seatId) {
        return transactionTemplate.execute(status -> {
            Flight flight = flightRepository.findById(flightId).orElseThrow();
            Booking booking = new Booking();
            booking.setFlight(flight);
            booking.setUser(userRepository.findById(userId).orElseThrow());
            booking.setBookingRef(UUID.randomUUID().toString());
            booking.setSeatNumber("standard-" + seatId);
            booking.setStatus(Booking.BookingStatus.BOOKED);
            flight.setStandardSeatsCount(flight.getStandardSeatsCount() - 1);
            return bookingRepository.save(booking).getId();
        });
    }

    private void checkBookings(long count, int seats) {
        Long booked = transactionTemplate.execute(status -> entityManager.createQuery(
                        "select count(b) from Booking b where b.flight.id = :flightId and b.status = :status", Long.class)
                .setParameter("flightId", flightId).setParameter("status", Booking.BookingStatus.BOOKED).getSingleResult());
        Assert.assertEquals(count, booked.longValue());
        Assert.assertEquals(seats, flightRepository.findById(flightId).orElseThrow().getStandardSeatsCount());
    }

    private void login(String email) {
        authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, "TestPassword1"));
    }

    // Keep the first transaction open so the second must wait for its database lock.
    private void runConcurrently(Runnable firstAction, Runnable secondAction) throws Exception {
        runConcurrently(firstAction, secondAction, true);
    }

    private void runConcurrently(Runnable firstAction, Runnable secondAction, boolean shouldWait) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        firstAction.run();
                        locked.countDown();
                        await(release);
                    });
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            Assert.assertTrue("First transaction did not finish its action", locked.await(10, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try {
                    secondStarted.countDown();
                    secondAction.run();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            Assert.assertTrue("Second transaction did not start", secondStarted.await(5, TimeUnit.SECONDS));
            if (shouldWait) {
                try {
                    second.get(500, TimeUnit.MILLISECONDS);
                    Assert.fail("Second transaction should wait for the first lock");
                } catch (TimeoutException expected) {
                    // The first transaction still holds its lock.
                }
            } else {
                second.get(5, TimeUnit.SECONDS);
            }
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            Assert.assertTrue("Workers did not stop", executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out waiting to release lock");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding lock", e);
        }
    }
}

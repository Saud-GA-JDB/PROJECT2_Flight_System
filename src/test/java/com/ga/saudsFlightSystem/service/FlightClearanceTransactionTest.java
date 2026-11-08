package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.BusinessConflictException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "stripe.secret-key=sk_test_unused", "stripe.success-url=http://localhost/payments/success",
        "stripe.cancel-url=http://localhost/payments/cancel"
})
@AutoConfigureMockMvc
@Transactional
public class FlightClearanceTransactionTest {
    @Autowired FlightOperationService service;
    @Autowired FlightReadinessService readiness;
    @Autowired FlightRepository flights;
    @Autowired CrewAssignmentRepository crew;
    @Autowired FlightInspectionRepository inspections;
    @Autowired FlightControlEventRepository events;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;

    @Autowired AirplaneRepository airplanes;
    @Autowired AirlineEmployeeRepository employees;
    @Autowired ExecutorService applicationExecutor;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean NotificationService notifications;
    @MockitoSpyBean AuditLogService audits;
    List<Object> fixtures = new ArrayList<>();
    boolean committed;

    final Instant now = Instant.parse("2026-11-07T07:00:00Z");
    Airline airline;
    Airplane plane;
    ControlFacility origin;
    ControlFacility destination;
    ControlFacility other;
    Flight flight;
    User pilot;
    User secondary;
    User attendant;
    User unassigned;
    User admin;
    User foreignAdmin;
    User originController;
    User destinationAdmin;
    User otherController;
    String encoded;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        encoded = passwords.encode("TestPassword1");
        airline = airline();
        plane = plane(airline);
        AirNavigationServiceProvider provider = new AirNavigationServiceProvider();
        provider.setName("Operations test provider");
        provider.setContactEmail("operations@example.com");
        persistTracked(provider);
        origin = tower(provider);
        destination = tower(provider);
        other = tower(provider);
        flight = flight(plane, origin, destination);
        pilot = employee(airline, AirlineEmployee.AirlineRole.PILOT);
        secondary = employee(airline, AirlineEmployee.AirlineRole.PILOT);
        attendant = employee(airline, AirlineEmployee.AirlineRole.FLIGHT_ATTENDANT);
        unassigned = employee(airline, AirlineEmployee.AirlineRole.PILOT);
        admin = employee(airline, AirlineEmployee.AirlineRole.ADMIN);
        foreignAdmin = employee(airline(), AirlineEmployee.AirlineRole.ADMIN);
        originController = atc(origin, ATCEmployee.ATCRole.CONTROLLER);
        destinationAdmin = atc(destination, ATCEmployee.ATCRole.ADMIN);
        otherController = atc(other, ATCEmployee.ATCRole.CONTROLLER);
        assign(flight, pilot, Duty.PILOT);
        assign(flight, secondary, Duty.SECONDARY_PILOT);
        assign(flight, attendant, Duty.FLIGHT_ATTENDANT);
        inspection(FlightInspection.InspectionStatus.PASSED);
        em.flush();
        login(pilot);
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
        notifications.closeConnections();
        if (committed) {
            if (TestTransaction.isActive()) TestTransaction.end();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                em.createQuery("delete from AuditLog a where a.entityType = :type and a.entityId in (select e.id from FlightControlEvent e where e.flight.id = :flight)")
                        .setParameter("type", AuditLog.EntityType.FLIGHT_CONTROL_EVENT).setParameter("flight", flight.getId()).executeUpdate();
                em.createQuery("delete from FlightControlEvent e where e.flight.id = :flight").setParameter("flight", flight.getId()).executeUpdate();
                for (int i = fixtures.size() - 1; i >= 0; i--) {
                    Object value = fixtures.get(i);
                    Object id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(value);
                    Object stored = em.find(value.getClass(), id);
                    if (stored != null) em.remove(stored);
                    em.flush();
                }
            });
        }
    }

    @Test
    public void twoRequestsSerializeWithOneWinner() throws Exception {
        race(pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L),
                secondary, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        Assert.assertEquals(ClearanceStatus.PENDING, flights.findById(flight.getId()).get().getClearanceStatus());
        assertWrites(1);
    }

    @Test
    public void approveVersusRejectSerializesWithOneWinner() throws Exception {
        pending(flight, FlightOperation.DEPARTURE, origin);
        race(originController, () -> service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, ClearanceStatus.APPROVED, null),
                originController, () -> service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, ClearanceStatus.REJECTED, "Traffic"));
        Assert.assertEquals(ClearanceStatus.APPROVED, flights.findById(flight.getId()).get().getClearanceStatus());
        assertWrites(1);
    }

    @Test
    public void twoApprovalsCannotExtendOrDuplicateApproval() throws Exception {
        pending(flight, FlightOperation.DEPARTURE, origin);
        race(originController, () -> service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, null),
                originController, () -> service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, ClearanceStatus.APPROVED, null));
        Assert.assertEquals(1, flights.findById(flight.getId()).get().getClearanceCycle());
        assertWrites(1);
    }

    @Test
    public void directApprovalVersusRequestCannotReplaceTheWinningCycle() throws Exception {
        race(originController, () -> service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, null),
                pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        Assert.assertEquals(ClearanceStatus.APPROVED, flights.findById(flight.getId()).get().getClearanceStatus());
        assertWrites(1);
    }

    @Test
    public void lockWaitCrossingExpiryRejectsRevocationAtTheBoundary() throws Exception {
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(1200));
        java.util.concurrent.atomic.AtomicReference<Instant> actionTime = new java.util.concurrent.atomic.AtomicReference<>(now.minusNanos(1));
        race(originController, () -> flights.findByIdForUpdate(flight.getId()), originController, () -> {
            try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
                time.when(Instant::now).thenAnswer(invocation -> actionTime.get());
                service.revokeClearance(flight.getId(), FlightOperation.DEPARTURE, 2L, "Traffic");
            }
        }, () -> actionTime.set(now));
        Assert.assertEquals(ClearanceStatus.APPROVED, flights.findById(flight.getId()).get().getClearanceStatus());
        assertWrites(0);
    }

    @Test
    public void departureRequestRechecksAirplaneOccupancyAfterWaitingForItsLock() throws Exception {
        Flight occupied = flight(plane, origin, destination);
        race(originController, () -> {
            airplanes.findByIdForUpdate(plane.getId());
            Flight otherFlight = flights.findByIdForUpdate(occupied.getId()).get();
            otherFlight.setActualDeparture(LocalDateTime.now().minusMinutes(1));
            otherFlight.setStatus(Flight.FlightStatus.IN_AIR);
        }, pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        Assert.assertEquals(ClearanceStatus.NONE, flights.findById(flight.getId()).get().getClearanceStatus());
        assertWrites(0);
    }

    @Test
    public void departureRequestLocksExtraCrewAndRechecksTheirOccupancy() throws Exception {
        User extra = employee(airline, AirlineEmployee.AirlineRole.PILOT);
        assign(flight, extra, Duty.PILOT);
        Flight occupied = flight(plane(airline), origin, destination);
        assign(occupied, extra, Duty.PILOT);
        race(originController, () -> {
            airplanes.findByIdForUpdate(occupied.getAirplane().getId());
            Flight otherFlight = flights.findByIdForUpdate(occupied.getId()).get();
            employees.findByIdForAssignment(extra.getAirlineEmployee().getId());
            otherFlight.setActualDeparture(LocalDateTime.now().minusMinutes(1));
            otherFlight.setStatus(Flight.FlightStatus.IN_AIR);
        }, pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        assertWrites(0);
    }

    @Test
    public void auditFailureRollsBackFlightEventAndNotifications() throws Exception {
        commitFixtures();
        AtomicInteger sent = subscribe(pilot);
        doThrow(new IllegalStateException("Injected audit failure")).when(audits).addAuditLog(any(), any(), any(), any(), any());
        Assert.assertThrows(IllegalStateException.class, () -> runAs(pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L)));
        reset(audits);
        TestTransaction.start();
        Assert.assertEquals(ClearanceStatus.NONE, flights.findById(flight.getId()).get().getClearanceStatus());
        assertWrites(0);
        Assert.assertEquals(0, sent.get());
    }

    @Test
    public void rollbackAfterCommandDiscardsAllWritesAndQueuedNotifications() throws Exception {
        commitFixtures();
        AtomicInteger sent = subscribe(pilot);
        Assert.assertThrows(IllegalStateException.class, () -> runAs(pilot, () -> {
            service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L);
            Assert.assertEquals(0, sent.get());
            throw new IllegalStateException("Rollback after command");
        }));
        TestTransaction.start();
        assertWrites(0);
        Assert.assertEquals(0, flights.findById(flight.getId()).get().getClearanceRevision());
        Assert.assertEquals(0, sent.get());
    }

    @Test
    public void notificationsReachOnlyScopedUsersAndOnlyAfterCommit() throws Exception {
        commitFixtures();
        AtomicInteger primary = subscribe(pilot);
        AtomicInteger second = subscribe(secondary);
        AtomicInteger tower = subscribe(originController);
        AtomicInteger airlineAdmin = subscribe(admin);
        AtomicInteger foreignTower = subscribe(destinationAdmin);
        AtomicInteger cabin = subscribe(attendant);
        runAs(pilot, () -> {
            service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L);
            Assert.assertEquals(0, primary.get());
            Assert.assertEquals(0, tower.get());
        });
        Assert.assertEquals(1, primary.get());
        Assert.assertEquals(1, second.get());
        Assert.assertEquals(1, tower.get());
        Assert.assertEquals(1, airlineAdmin.get());
        Assert.assertEquals(0, foreignTower.get());
        Assert.assertEquals(0, cabin.get());
        TestTransaction.start();
        assertWrites(1);
    }

    @Test
    public void deliveryFailureCannotUndoCommittedCommand() throws Exception {
        commitFixtures();
        SseEmitter emitter = mock(SseEmitter.class);
        doReturn(emitter).when(notifications).createEmitter();
        notifications.subscribe(pilot);
        doThrow(new java.io.IOException("Disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        runAs(pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        TestTransaction.start();
        assertWrites(1);
        Assert.assertEquals(ClearanceStatus.PENDING, flights.findById(flight.getId()).get().getClearanceStatus());
    }

    @Test
    public void failingEmitterCleanupDoesNotFailTheResponseOrOtherRecipients() throws Exception {
        commitFixtures();
        SseEmitter emitter = mock(SseEmitter.class);
        doReturn(emitter).when(notifications).createEmitter();
        notifications.subscribe(pilot);
        doThrow(new IllegalStateException("Disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("Already closed")).when(emitter).completeWithError(any());
        AtomicInteger second = subscribe(secondary);
        runAs(pilot, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        Assert.assertEquals(1, second.get());
        TestTransaction.start();
        assertWrites(1);
    }

    private AtomicInteger subscribe(User user) throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicInteger sent = new AtomicInteger();
        doAnswer(invocation -> {
            SseEmitter.SseEventBuilder event = invocation.getArgument(0);
            if (event.build().stream().anyMatch(data -> data.getData() instanceof String text && text.contains("event:Clearance_")))
                sent.incrementAndGet();
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doReturn(emitter).when(notifications).createEmitter();
        notifications.subscribe(user);
        return sent;
    }

    private void race(User firstUser, Runnable firstAction, User secondUser, Runnable secondAction) throws Exception {
        race(firstUser, firstAction, secondUser, secondAction, () -> {});
    }

    private void race(User firstUser, Runnable firstAction, User secondUser, Runnable secondAction, Runnable afterBlocked) throws Exception {
        commitFixtures();
        CountDownLatch firstChanged = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        Future<?> first = null;
        Future<?> second = null;
        try {
            first = applicationExecutor.submit(() -> runAs(firstUser, () -> {
                firstAction.run();
                em.flush();
                firstChanged.countDown();
                await(release);
            }));
            Assert.assertTrue(firstChanged.await(15, TimeUnit.SECONDS));
            second = applicationExecutor.submit(() -> runAs(secondUser, () -> {
                secondPid.set(((Number) em.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue());
                secondStarted.countDown();
                secondAction.run();
            }));
            Assert.assertTrue(secondStarted.await(15, TimeUnit.SECONDS));
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !second.isDone()) {
                blocked = Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status ->
                        em.createNativeQuery("select cardinality(pg_blocking_pids(:pid)) > 0").setParameter("pid", secondPid.get()).getSingleResult()));
                if (blocked) break;
                Thread.sleep(50);
            }
            Assert.assertTrue("Competing command must wait for the winning transaction", blocked);
            afterBlocked.run();
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            Future<?> loser = second;
            ExecutionException failure = Assert.assertThrows(ExecutionException.class, () -> loser.get(15, TimeUnit.SECONDS));
            Assert.assertTrue(failure.getCause() instanceof BusinessConflictException);
        } finally {
            release.countDown();
            if (first != null) settle(first);
            if (second != null) settle(second);
            TestTransaction.start();
        }
    }

    private void commitFixtures() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committed = true;
    }

    private void runAs(User user, Runnable action) {
        try {
            login(user);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                em.createNativeQuery("set local lock_timeout = '10s'").executeUpdate();
                action.run();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for release");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void settle(Future<?> future) throws Exception {
        try { future.get(15, TimeUnit.SECONDS); }
        catch (ExecutionException expected) { /* The competing request may lose. */ }
    }

    private void persistTracked(Object value) {
        em.persist(value);
        fixtures.add(value);
    }

    private void assertWrites(long count) {
        Assert.assertEquals(count, events.countHistory(flight.getId(), null, null));
        Assert.assertEquals(count, em.createQuery("select count(a) from AuditLog a where a.entityType = :type and a.entityId in (select e.id from FlightControlEvent e where e.flight.id = :flight)", Long.class)
                .setParameter("type", AuditLog.EntityType.FLIGHT_CONTROL_EVENT).setParameter("flight", flight.getId()).getSingleResult().longValue());
    }

    private void approve(FlightOperation operation, ControlFacility tower, Instant at) {
        flight.setClearanceOperation(operation);
        flight.setClearanceFacility(tower);
        flight.setClearanceStatus(ClearanceStatus.APPROVED);
        flight.setClearanceCycle(1);
        flight.setClearanceRevision(2);
        flight.setClearanceApprovedAt(at);
        flight.setClearanceExpiresAt(at.plusSeconds(1200));
    }

    private void pending(Flight value, FlightOperation operation, ControlFacility tower) {
        value.setClearanceOperation(operation);
        value.setClearanceFacility(tower);
        value.setClearanceStatus(ClearanceStatus.PENDING);
        value.setClearanceCycle(1);
        value.setClearanceRevision(1);
    }

    private Airline airline() {
        Airline value = new Airline();
        value.setName(UUID.randomUUID().toString());
        value.setAirlineCode(UUID.randomUUID().toString());
        persistTracked(value);
        return value;
    }

    private Airplane plane(Airline owner) {
        Airplane value = new Airplane();
        value.setAirline(owner);
        value.setRegistrationNumber(UUID.randomUUID().toString());
        value.setStatus(Airplane.Status.ACTIVE);
        persistTracked(value);
        return value;
    }

    private ControlFacility tower(AirNavigationServiceProvider provider) {
        Airport airport = new Airport();
        airport.setIataCode(UUID.randomUUID().toString());
        airport.setTimeZone("Asia/Bahrain");
        persistTracked(airport);
        ControlFacility value = new ControlFacility();
        value.setName("Test tower");
        value.setAirport(airport);
        value.setProvider(provider);
        persistTracked(value);
        return value;
    }

    private Flight flight(Airplane airplane, ControlFacility from, ControlFacility to) {
        Flight value = new Flight();
        value.setFlightNumber(UUID.randomUUID().toString());
        value.setAirplane(airplane);
        value.setAirline(airplane.getAirline());
        value.setOriginAirport(from.getAirport());
        value.setDestinationAirport(to.getAirport());
        value.setStatus(Flight.FlightStatus.ACTIVE);
        value.setScheduledDeparture(LocalDateTime.of(2026, 11, 7, 10, 0));
        value.setScheduledArrival(value.getScheduledDeparture().plusHours(2));
        persistTracked(value);
        return value;
    }

    private void assign(Flight value, User user, Duty duty) {
        CrewAssignment assignment = new CrewAssignment();
        assignment.setFlight(value);
        assignment.setAirlineEmployee(user.getAirlineEmployee());
        assignment.setDuty(duty);
        persistTracked(assignment);
    }

    private void inspection(FlightInspection.InspectionStatus status) {
        FlightInspection value = new FlightInspection();
        value.setFlight(flight);
        value.setAirport(origin.getAirport());
        value.setStatus(status);
        persistTracked(value);
    }

    private User employee(Airline company, AirlineEmployee.AirlineRole role) {
        AirlineEmployee profile = new AirlineEmployee();
        profile.setAirline(company);
        profile.setAirlineRole(role);
        User user = user(profile);
        user.setRole(User.Role.AIRLINE_EMPLOYEE);
        user.setAirlineEmployee(profile);
        users.save(user);
        fixtures.add(profile);
        fixtures.add(user);
        return user;
    }

    private User atc(ControlFacility tower, ATCEmployee.ATCRole role) {
        ATCEmployee profile = new ATCEmployee();
        profile.setFacility(tower);
        profile.setAtcRole(role);
        profile.setEmployeeNumber(UUID.randomUUID().toString().toUpperCase());
        User user = user(profile);
        user.setRole(User.Role.ATC_EMPLOYEE);
        user.setAtcEmployee(profile);
        users.save(user);
        fixtures.add(profile);
        fixtures.add(user);
        return user;
    }

    private User user(Person profile) {
        profile.setCpr(String.valueOf(ThreadLocalRandom.current().nextLong(100000000, 1000000000)));
        profile.setFName("Test");
        profile.setLName("Operations");
        User user = new User();
        user.setEmailAddress(UUID.randomUUID() + "@example.com");
        user.setPassword(encoded);
        user.setActive(true);
        user.setStatus(User.Status.ACTIVE);
        return user;
    }

    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmailAddress(), "TestPassword1")));
    }

}

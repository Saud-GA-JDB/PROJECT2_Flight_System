package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.FlightInspectionResponse;
import com.ga.saudsFlightSystem.repository.FlightInspectionRepository;
import jakarta.persistence.EntityManager;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
@Transactional
public class FlightInspectionServiceTest {
    @Autowired FlightInspectionService service;
    @Autowired FlightInspectionRepository repository;
    @Autowired EntityManager entityManager;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;

    Long flightId;
    Long otherFlightId;
    Long airplaneId;
    Long inspectorId;
    Long secondInspectorId;
    Long otherInspectorId;
    Long baggageHandlerId;
    String airportAdminEmail;
    String secondAdminEmail;
    String otherAdminEmail;
    String inspectorEmail;
    String secondInspectorEmail;
    String airlineAdminEmail;
    String otherAirlineAdminEmail;
    String baggageHandlerEmail;
    String encodedPassword;
    boolean committedFixtures;
    List<Long> userIds = new ArrayList<>();
    List<Long> employeeIds = new ArrayList<>();
    List<Long> airlineEmployeeIds = new ArrayList<>();
    List<Long> airlineIds = new ArrayList<>();
    List<Long> airportIds = new ArrayList<>();
    List<Long> operatorIds = new ArrayList<>();

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        encodedPassword = passwordEncoder.encode("TestPassword1");
        Airline airline = airline();
        Airline otherAirline = airline();
        Airport airport = airport();
        Airport otherAirport = airport();
        Airplane airplane = new Airplane();
        airplane.setAirline(airline);
        airplane.setRegistrationNumber("TEST-" + UUID.randomUUID());
        airplane.setStatus(Airplane.Status.GROUNDED);
        entityManager.persist(airplane);
        airplaneId = airplane.getId();
        flightId = flight(airline, airplane, airport, otherAirport).getId();
        otherFlightId = flight(otherAirline, airplane, otherAirport, airport).getId();

        airportAdminEmail = airportUser(airport, AirportEmployee.AirportRole.ADMIN).getEmailAddress();
        secondAdminEmail = airportUser(airport, AirportEmployee.AirportRole.ADMIN).getEmailAddress();
        otherAdminEmail = airportUser(otherAirport, AirportEmployee.AirportRole.ADMIN).getEmailAddress();
        User inspector = airportUser(airport, AirportEmployee.AirportRole.INSPECTOR);
        inspectorId = inspector.getAirportEmployee().getId();
        inspectorEmail = inspector.getEmailAddress();
        User second = airportUser(airport, AirportEmployee.AirportRole.INSPECTOR);
        secondInspectorId = second.getAirportEmployee().getId();
        secondInspectorEmail = second.getEmailAddress();
        otherInspectorId = airportUser(otherAirport, AirportEmployee.AirportRole.INSPECTOR).getAirportEmployee().getId();
        User baggageHandler = airportUser(airport, AirportEmployee.AirportRole.BAGGAGE_HANDLER);
        baggageHandlerId = baggageHandler.getAirportEmployee().getId();
        baggageHandlerEmail = baggageHandler.getEmailAddress();
        airlineAdminEmail = airlineUser(airline).getEmailAddress();
        otherAirlineAdminEmail = airlineUser(otherAirline).getEmailAddress();
        entityManager.flush();
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
        if (!committedFixtures) return;
        if (!TestTransaction.isActive()) TestTransaction.start();
        entityManager.clear();
        List<Long> flights = List.of(flightId, otherFlightId);
        entityManager.createQuery("DELETE FROM AuditLog a WHERE a.entityType = :type AND a.entityId IN (SELECT i.id FROM FlightInspection i WHERE i.flight.id IN :ids)")
                .setParameter("type", AuditLog.EntityType.FLIGHT_INSPECTION).setParameter("ids", flights).executeUpdate();
        entityManager.createQuery("DELETE FROM FlightInspection i WHERE i.flight.id IN :ids").setParameter("ids", flights).executeUpdate();
        entityManager.createQuery("DELETE FROM User u WHERE u.id IN :ids").setParameter("ids", userIds).executeUpdate();
        entityManager.createQuery("DELETE FROM AirportEmployee e WHERE e.id IN :ids").setParameter("ids", employeeIds).executeUpdate();
        entityManager.createQuery("DELETE FROM AirlineEmployee e WHERE e.id IN :ids").setParameter("ids", airlineEmployeeIds).executeUpdate();
        entityManager.createQuery("DELETE FROM Flight f WHERE f.id IN :ids").setParameter("ids", flights).executeUpdate();
        entityManager.remove(entityManager.find(Airplane.class, airplaneId));
        entityManager.flush();
        entityManager.createQuery("DELETE FROM Airport a WHERE a.id IN :ids").setParameter("ids", airportIds).executeUpdate();
        entityManager.createQuery("DELETE FROM AirportOperator o WHERE o.id IN :ids").setParameter("ids", operatorIds).executeUpdate();
        entityManager.createQuery("DELETE FROM Airline a WHERE a.id IN :ids").setParameter("ids", airlineIds).executeUpdate();
        TestTransaction.flagForCommit();
    }

    @Test
    public void whenInspectionFailsThenReinspectionPreservesHistory() {
        Long firstId = assign();
        login(inspectorEmail);
        service.completeInspection(flightId, firstId, FlightInspection.InspectionStatus.FAILED, "Check landing gear");
        login(airlineAdminEmail);
        Assert.assertEquals(HttpStatus.CREATED, service.requestReInspection(flightId).getStatusCode());
        FlightInspection second = latestInspection();
        Long secondId = second.getId();
        Assert.assertNotEquals(firstId, secondId);
        Assert.assertEquals(FlightInspection.InspectionStatus.PENDING, second.getStatus());
        Assert.assertNull(second.getInspector());
        Assert.assertNull(second.getReason());
        Assert.assertNull(second.getCompletedAt());
        login(airportAdminEmail);
        service.assignInspector(flightId, secondInspectorId);
        login(secondInspectorEmail);
        service.completeInspection(flightId, secondId, FlightInspection.InspectionStatus.PASSED, "Landing gear checked");
        entityManager.flush();
        entityManager.clear();
        FlightInspection previous = entityManager.find(FlightInspection.class, firstId);
        FlightInspection completed = entityManager.find(FlightInspection.class, secondId);
        Assert.assertEquals(FlightInspection.InspectionStatus.FAILED, previous.getStatus());
        Assert.assertEquals("Check landing gear", previous.getReason());
        Assert.assertNotNull(previous.getCompletedAt());
        Assert.assertEquals(FlightInspection.InspectionStatus.PASSED, completed.getStatus());
        Assert.assertEquals(secondInspectorId, completed.getInspector().getId());
        Assert.assertEquals("Landing gear checked", completed.getReason());
        Assert.assertNotNull(completed.getCompletedAt());
        Assert.assertEquals(Airplane.Status.GROUNDED, entityManager.find(Airplane.class, airplaneId).getStatus());
        assertAuditCount(AuditLog.Action.FLIGHT_INSPECTOR_ASSIGNED, 2);
        assertAuditCount(AuditLog.Action.FLIGHT_INSPECTION_COMPLETED, 2);
        assertAuditCount(AuditLog.Action.FLIGHT_REINSPECTION_REQUESTED, 1);
        login(airlineAdminEmail);
        List<?> history = (List<?>) service.getFlightInspections(flightId).getBody();
        Assert.assertNotNull(history);
        Assert.assertEquals(2, history.size());
        Assert.assertEquals(secondId, ((FlightInspectionResponse) history.get(0)).getId());
        Assert.assertEquals(firstId, ((FlightInspectionResponse) history.get(1)).getId());
    }

    @Test
    public void whenWrongAirportAssignsThenNoInspectionIsCreated() {
        login(otherAdminEmail);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.assignInspector(flightId, inspectorId));
        Assert.assertTrue(repository.findByFlight_IdOrderByIdDesc(flightId).isEmpty());
    }

    @Test
    public void whenInspectorAssignsThenRequestIsRejected() {
        login(inspectorEmail);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.assignInspector(flightId, inspectorId));
    }

    @Test
    public void whenInspectorBelongsToAnotherAirportThenAssignmentIsRejected() {
        login(airportAdminEmail);
        Assert.assertThrows(InvalidInformationException.class, () -> service.assignInspector(flightId, otherInspectorId));
    }

    @Test
    public void whenBaggageHandlerIsSelectedThenAssignmentIsRejected() {
        login(airportAdminEmail);
        Assert.assertThrows(InvalidInformationException.class, () -> service.assignInspector(flightId, baggageHandlerId));
    }

    @Test
    public void whenUnassignedInspectorCompletesThenInspectionStaysPending() {
        Long id = assign();
        login(secondInspectorEmail);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.completeInspection(flightId, id, FlightInspection.InspectionStatus.PASSED, "Not assigned"));
        FlightInspection inspection = repository.findFirstByFlight_IdOrderByIdDesc(flightId).orElseThrow();
        Assert.assertEquals(FlightInspection.InspectionStatus.PENDING, inspection.getStatus());
        Assert.assertNull(inspection.getReason());
        Assert.assertNull(inspection.getCompletedAt());
    }

    @Test
    public void whenBaggageHandlerCompletesThenRequestIsRejected() {
        Long id = assign();
        login(baggageHandlerEmail);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.completeInspection(flightId, id, FlightInspection.InspectionStatus.PASSED, null));
    }

    @Test
    public void whenOtherAirlineRequestsReinspectionThenHistoryIsUnchanged() {
        failInspection();
        login(otherAirlineAdminEmail);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.requestReInspection(flightId));
        Assert.assertEquals(1, repository.findByFlight_IdOrderByIdDesc(flightId).size());
    }

    @Test
    public void whenLatestInspectionIsPendingThenReinspectionIsRejected() {
        assign();
        login(airlineAdminEmail);
        Assert.assertThrows(InvalidInformationException.class, () -> service.requestReInspection(flightId));
    }

    @Test
    public void whenLatestInspectionPassedThenReinspectionIsRejected() {
        Long id = assign();
        login(inspectorEmail);
        service.completeInspection(flightId, id, FlightInspection.InspectionStatus.PASSED, null);
        login(airlineAdminEmail);
        Assert.assertThrows(InvalidInformationException.class, () -> service.requestReInspection(flightId));
    }

    @Test
    public void whenListsAreRequestedThenOnlyAuthorizedInspectionsAreReturned() {
        Long id = assign();
        login(otherAdminEmail);
        service.assignInspector(otherFlightId, otherInspectorId);
        login(airportAdminEmail);
        assertOnlyInspection(service.getAirportInspections().getBody(), id);
        login(inspectorEmail);
        assertOnlyInspection(service.getMyInspections().getBody(), id);
        login(secondInspectorEmail);
        Assert.assertTrue(((List<?>) service.getMyInspections().getBody()).isEmpty());
        login(airlineAdminEmail);
        assertOnlyInspection(service.getFlightInspections(flightId).getBody(), id);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getFlightInspections(otherFlightId));
    }

    @Test
    public void whenBaggageHandlerListsInspectionsThenAccessIsRejected() {
        login(baggageHandlerEmail);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getAirportInspections());
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getMyInspections());
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getFlightInspections(flightId));
    }

    @Test
    public void whenAssignmentsCompeteThenOnlyFirstInspectorIsAssigned() throws Exception {
        checkCompetingRequests(airportAdminEmail, secondAdminEmail,
                () -> service.assignInspector(flightId, inspectorId),
                () -> service.assignInspector(flightId, secondInspectorId));
        Assert.assertEquals(1, repository.findByFlight_IdOrderByIdDesc(flightId).size());
        Assert.assertEquals(inspectorId, latestInspection().getInspector().getId());
        assertAuditCount(AuditLog.Action.FLIGHT_INSPECTOR_ASSIGNED, 1);
    }

    @Test
    public void whenCompletionsCompeteThenFirstResultAndReasonRemain() throws Exception {
        Long id = assign();
        checkCompetingRequests(inspectorEmail, inspectorEmail,
                () -> service.completeInspection(flightId, id, FlightInspection.InspectionStatus.FAILED, "First reason"),
                () -> service.completeInspection(flightId, id, FlightInspection.InspectionStatus.PASSED, "Second reason"));
        FlightInspection inspection = latestInspection();
        Assert.assertEquals(FlightInspection.InspectionStatus.FAILED, inspection.getStatus());
        Assert.assertEquals("First reason", inspection.getReason());
        Assert.assertNotNull(inspection.getCompletedAt());
        assertAuditCount(AuditLog.Action.FLIGHT_INSPECTION_COMPLETED, 1);
    }

    @Test
    public void whenReinspectionRequestsCompeteThenOnlyOnePendingAttemptIsCreated() throws Exception {
        Long failedId = failInspection();
        checkCompetingRequests(airlineAdminEmail, airlineAdminEmail,
                () -> service.requestReInspection(flightId), () -> service.requestReInspection(flightId));
        Assert.assertEquals(2, repository.findByFlight_IdOrderByIdDesc(flightId).size());
        FlightInspection latest = latestInspection();
        Assert.assertEquals(FlightInspection.InspectionStatus.PENDING, latest.getStatus());
        Assert.assertNull(latest.getInspector());
        Assert.assertEquals(FlightInspection.InspectionStatus.FAILED, entityManager.find(FlightInspection.class, failedId).getStatus());
        assertAuditCount(AuditLog.Action.FLIGHT_REINSPECTION_REQUESTED, 1);
    }

    @Test
    public void whenDuplicatePendingAttemptIsInsertedThenDatabaseRejectsIt() {
        assign();
        FlightInspection existing = latestInspection();
        FlightInspection duplicate = new FlightInspection();
        duplicate.setFlight(existing.getFlight());
        duplicate.setAirport(existing.getAirport());
        RuntimeException failure = Assert.assertThrows(RuntimeException.class, () -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        });
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        Assert.assertNotNull("Expected a PostgreSQL constraint error", cause);
        Assert.assertEquals("23505", ((SQLException) cause).getSQLState());
        Assert.assertTrue(cause.getMessage().contains("flight_inspections_one_pending_per_flight"));
    }

    private Long assign() {
        login(airportAdminEmail);
        Assert.assertEquals(HttpStatus.OK, service.assignInspector(flightId, inspectorId).getStatusCode());
        return latestInspection().getId();
    }

    private Long failInspection() {
        Long id = assign();
        login(inspectorEmail);
        service.completeInspection(flightId, id, FlightInspection.InspectionStatus.FAILED, "Check landing gear");
        return id;
    }

    private FlightInspection latestInspection() {
        entityManager.flush();
        entityManager.clear();
        return repository.findFirstByFlight_IdOrderByIdDesc(flightId).orElseThrow();
    }

    private void assertOnlyInspection(Object body, Long id) {
        Assert.assertTrue(body instanceof List<?>);
        List<?> inspections = (List<?>) body;
        Assert.assertEquals(1, inspections.size());
        FlightInspectionResponse response = (FlightInspectionResponse) inspections.get(0);
        Assert.assertEquals(id, response.getId());
        Assert.assertEquals(flightId, response.getFlightId());
        Assert.assertEquals(inspectorId, response.getInspectorId());
    }

    private void assertAuditCount(AuditLog.Action action, long expected) {
        Long count = entityManager.createQuery("SELECT COUNT(a) FROM AuditLog a WHERE a.entityType = :type AND a.action = :action AND a.entityId IN (SELECT i.id FROM FlightInspection i WHERE i.flight.id = :flightId)", Long.class)
                .setParameter("type", AuditLog.EntityType.FLIGHT_INSPECTION).setParameter("action", action)
                .setParameter("flightId", flightId).getSingleResult();
        Assert.assertEquals(Long.valueOf(expected), count);
    }

    private void checkCompetingRequests(String firstEmail, String secondEmail, Runnable firstAction, Runnable secondAction) throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedFixtures = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        try {
            Future<?> first = executor.submit(() -> runAs(firstEmail, () -> {
                firstAction.run();
                entityManager.flush();
                firstUpdated.countDown();
                await(release);
            }));
            Assert.assertTrue("First request did not update", firstUpdated.await(15, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> runAs(secondEmail, () -> {
                secondPid.set(((Number) entityManager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue());
                secondStarted.countDown();
                secondAction.run();
            }));
            Assert.assertTrue("Second request did not start", secondStarted.await(15, TimeUnit.SECONDS));
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !second.isDone()) {
                blocked = Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status ->
                        entityManager.createNativeQuery("SELECT cardinality(pg_blocking_pids(:pid)) > 0")
                                .setParameter("pid", secondPid.get()).getSingleResult()));
                if (blocked) break;
                Thread.sleep(50);
            }
            Assert.assertTrue("Second request must wait on a PostgreSQL lock", blocked);
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            ExecutionException failure = Assert.assertThrows(ExecutionException.class, () -> second.get(15, TimeUnit.SECONDS));
            Assert.assertEquals(InvalidInformationException.class, failure.getCause().getClass());
        } finally {
            release.countDown();
            executor.shutdownNow();
            boolean stopped = executor.awaitTermination(15, TimeUnit.SECONDS);
            TestTransaction.start();
            Assert.assertTrue("Workers did not stop", stopped);
        }
    }

    private void runAs(String email, Runnable action) {
        try {
            login(email);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out releasing lock");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void login(String email) {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, "TestPassword1")));
    }

    private Airline airline() {
        Airline airline = new Airline();
        airline.setName("Inspection test " + UUID.randomUUID());
        airline.setAirlineCode(UUID.randomUUID().toString());
        entityManager.persist(airline);
        airlineIds.add(airline.getId());
        return airline;
    }

    private Airport airport() {
        AirportOperator operator = new AirportOperator();
        operator.setName("Inspection test " + UUID.randomUUID());
        operator.setContactEmail("test@example.com");
        entityManager.persist(operator);
        operatorIds.add(operator.getId());
        Airport airport = new Airport();
        airport.setName("Inspection test airport");
        airport.setIataCode(UUID.randomUUID().toString());
        airport.setTimeZone("Asia/Bahrain");
        airport.setOperator(operator);
        entityManager.persist(airport);
        airportIds.add(airport.getId());
        return airport;
    }

    private Flight flight(Airline airline, Airplane airplane, Airport origin, Airport destination) {
        Flight flight = new Flight();
        flight.setFlightNumber("TEST-" + UUID.randomUUID());
        flight.setAirline(airline);
        flight.setAirplane(airplane);
        flight.setOriginAirport(origin);
        flight.setDestinationAirport(destination);
        flight.setStatus(Flight.FlightStatus.PENDING_CREW);
        flight.setScheduledDeparture(LocalDateTime.now().plusDays(1));
        flight.setScheduledArrival(LocalDateTime.now().plusDays(1).plusHours(2));
        entityManager.persist(flight);
        return flight;
    }

    private User airportUser(Airport airport, AirportEmployee.AirportRole role) {
        AirportEmployee employee = new AirportEmployee();
        employee.setCpr(cpr());
        employee.setAirport(airport);
        employee.setOperator(airport.getOperator());
        employee.setAirportRole(role);
        User user = user(User.Role.AIRPORT_EMPLOYEE);
        user.setAirportEmployee(employee);
        entityManager.persist(user);
        userIds.add(user.getId());
        employeeIds.add(employee.getId());
        return user;
    }

    private User airlineUser(Airline airline) {
        AirlineEmployee employee = new AirlineEmployee();
        employee.setCpr(cpr());
        employee.setAirline(airline);
        employee.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        User user = user(User.Role.AIRLINE_EMPLOYEE);
        user.setAirlineEmployee(employee);
        entityManager.persist(user);
        userIds.add(user.getId());
        airlineEmployeeIds.add(employee.getId());
        return user;
    }

    private User user(User.Role role) {
        User user = new User();
        user.setEmailAddress("inspection-test-" + UUID.randomUUID() + "@example.com");
        user.setPassword(encodedPassword);
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);
        return user;
    }

    private String cpr() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100000000L, 1000000000L));
    }
}

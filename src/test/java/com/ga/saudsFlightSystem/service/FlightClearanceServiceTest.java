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
public class FlightClearanceServiceTest {
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
    @Autowired FlightService flightService;
    @Autowired CrewAssignmentService crewService;
    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;

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
        em.persist(provider);
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
    }

    @Test
    public void requestApprovalRevocationAndFreshCycleWriteMatchingEventsAndAudits() {
        OperationResponse requested = service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L).getBody();
        Assert.assertEquals("PENDING", requested.getClearance().getStatus());
        Assert.assertEquals(1, requested.getClearance().getCycle());
        login(originController);
        OperationResponse approved = service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, ClearanceStatus.APPROVED, null).getBody();
        Assert.assertEquals(requested.getClearance().getCycle(), approved.getClearance().getCycle());
        Assert.assertEquals(Duration.ofMinutes(20), Duration.between(approved.getClearance().getApprovedAt(), approved.getClearance().getExpiresAt()));
        Instant expires = approved.getClearance().getExpiresAt();
        OperationResponse revoked = service.revokeClearance(flight.getId(), FlightOperation.DEPARTURE, 2L, "  Traffic conflict  ").getBody();
        Assert.assertEquals(expires, revoked.getClearance().getExpiresAt());
        Assert.assertEquals("Traffic conflict", events.findById(revoked.getEventId()).get().getReason());
        login(secondary);
        OperationResponse again = service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 3L).getBody();
        Assert.assertEquals(2, again.getClearance().getCycle());
        Assert.assertEquals(4, again.getClearance().getRevision());
        Assert.assertNull(again.getClearance().getApprovedAt());
        assertWrites(4);
    }

    @Test
    public void eitherPilotCanWithdrawAndPendingDoesNotExpire() {
        pending(flight, FlightOperation.DEPARTURE, origin);
        login(secondary);
        OperationResponse result = service.withdrawClearance(flight.getId(), FlightOperation.DEPARTURE, 1L).getBody();
        Assert.assertEquals("WITHDRAWN", result.getClearance().getStatus());
        Assert.assertEquals(1, result.getClearance().getCycle());
        Assert.assertEquals(secondary.getId(), events.findById(result.getEventId()).get().getActorUser().getId());
        assertWrites(1);
    }

    @Test
    public void directApprovalCreatesCycleOrApprovesPendingWithoutAnotherCycle() {
        login(originController);
        OperationResponse direct = service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, "  Ready  ").getBody();
        Assert.assertEquals(1, direct.getClearance().getCycle());
        service.revokeClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, "Traffic");
        login(pilot);
        service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 2L);
        login(originController);
        OperationResponse pendingApproval = service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 3L, null).getBody();
        Assert.assertEquals(2, pendingApproval.getClearance().getCycle());
        Assert.assertEquals(4, pendingApproval.getClearance().getRevision());
        assertWrites(4);
    }

    @Test
    public void rejectionAndWithdrawalRemainPossibleAfterReadinessFails() {
        pending(flight, FlightOperation.DEPARTURE, origin);
        plane.setStatus(Airplane.Status.GROUNDED);
        login(originController);
        service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 1L, ClearanceStatus.REJECTED, "Inspection concern");
        Assert.assertEquals(ClearanceStatus.REJECTED, flight.getClearanceStatus());
        // A separate pending request fixture remains withdrawable despite the grounded airplane.
        flight.setClearanceStatus(ClearanceStatus.PENDING);
        login(secondary);
        service.withdrawClearance(flight.getId(), FlightOperation.DEPARTURE, 2L);
        assertWrites(2);
    }

    @Test
    public void revocationDoesNotRequireOperationalReadinessAndAnyOwnTowerControllerCanRevoke() {
        approve(FlightOperation.DEPARTURE, origin, Instant.now());
        plane.setStatus(Airplane.Status.GROUNDED);
        User colleague = atc(origin, ATCEmployee.ATCRole.ADMIN);
        login(colleague);
        service.revokeClearance(flight.getId(), FlightOperation.DEPARTURE, 2L, "Safety concern");
        Assert.assertEquals(ClearanceStatus.REVOKED, flight.getClearanceStatus());
        assertWrites(1);
    }

    @Test
    public void staleRevisionAndDuplicatePendingNeverWrite() {
        pending(flight, FlightOperation.DEPARTURE, origin);
        Assert.assertThrows(BusinessConflictException.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 1L));
        Assert.assertThrows(BusinessConflictException.class, () -> service.withdrawClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        Assert.assertEquals(1, flight.getClearanceRevision());
        assertWrites(0);
    }

    @Test
    public void validApprovalCannotBeExtendedAndExpiredApprovalStartsFreshCycle() {
        approve(FlightOperation.DEPARTURE, origin, Instant.now());
        login(originController);
        Assert.assertThrows(BusinessConflictException.class, () -> service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 2L, null));
        assertWrites(0);
    }

    @Test
    public void expiredApprovalAllowsNewRequestButCannotBeRevoked() {
        approve(FlightOperation.DEPARTURE, origin, Instant.now().minusSeconds(1201));
        OperationResponse response = service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 2L).getBody();
        Assert.assertEquals(2, response.getClearance().getCycle());
        Assert.assertNull(response.getClearance().getExpiresAt());
        assertWrites(1);
    }

    @Test
    public void exactExpiryRejectsRevocationWithoutWrites() {
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(1200));
        login(originController);
        try (var time = org.mockito.Mockito.mockStatic(Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(now);
            Assert.assertThrows(BusinessConflictException.class, () -> service.revokeClearance(flight.getId(), FlightOperation.DEPARTURE, 2L, "Expired"));
        }
        assertWrites(0);
    }

    @Test
    public void arrivalUsesDestinationWithoutInspectionOrActiveAirplaneRequirement() {
        flight.setStatus(Flight.FlightStatus.IN_AIR);
        flight.setActualDeparture(LocalDateTime.ofInstant(Instant.now().minusSeconds(60), ZoneId.of("Asia/Bahrain")));
        approve(FlightOperation.DEPARTURE, origin, Instant.now().minusSeconds(60));
        flight.setClearanceStatus(ClearanceStatus.CONSUMED);
        plane.setStatus(Airplane.Status.GROUNDED);
        inspection(FlightInspection.InspectionStatus.FAILED);
        service.requestClearance(flight.getId(), FlightOperation.ARRIVAL, 2L);
        login(destinationAdmin);
        OperationResponse response = service.decideClearance(flight.getId(), FlightOperation.ARRIVAL, 3L, ClearanceStatus.APPROVED, null).getBody();
        Assert.assertEquals(destination.getId(), response.getClearance().getFacilityId());
        Assert.assertEquals(2, response.getClearance().getCycle());
        Assert.assertEquals(Flight.FlightStatus.IN_AIR, response.getStatus());
        Assert.assertNull(response.getActualArrival());
        assertWrites(2);
    }

    @Test
    public void removedPilotAttendantInactiveAccountAndForeignTowerAreDenied() {
        login(unassigned);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        login(attendant);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        login(otherController);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, null));
        login(pilot);
        pilot.setActive(false);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        assertWrites(0);
    }

    @Test
    public void readinessAndPhaseConflictsLeaveStateUntouched() {
        plane.setStatus(Airplane.Status.GROUNDED);
        Assert.assertThrows(BusinessConflictException.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, 0L));
        Assert.assertThrows(BusinessConflictException.class, () -> service.requestClearance(flight.getId(), FlightOperation.ARRIVAL, 0L));
        assertWrites(0);
    }

    @Test
    public void invalidRevisionOperationDecisionAndReasonAreBadInput() {
        Assert.assertThrows(InvalidInformationException.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, null));
        Assert.assertThrows(InvalidInformationException.class, () -> service.requestClearance(flight.getId(), FlightOperation.DEPARTURE, -1L));
        Assert.assertThrows(InvalidInformationException.class, () -> service.withdrawClearance(flight.getId(), null, 0L));
        Assert.assertThrows(InvalidInformationException.class, () -> service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, ClearanceStatus.PENDING, null));
        Assert.assertThrows(InvalidInformationException.class, () -> service.decideClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, ClearanceStatus.REJECTED, "  "));
        Assert.assertThrows(InvalidInformationException.class, () -> service.revokeClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, "x".repeat(2001)));
        assertWrites(0);
    }

    @Test
    public void notificationRecipientsAreActiveScopedUserIds() {
        User inactive = atc(origin, ATCEmployee.ATCRole.CONTROLLER);
        inactive.setActive(false);
        em.flush();
        List<Long> recipients = users.findClearanceRecipients(flight.getId(), origin.getId(), airline.getId(), User.Status.ACTIVE.ordinal());
        Assert.assertEquals(4, recipients.size());
        Assert.assertTrue(recipients.containsAll(List.of(pilot.getId(), secondary.getId(), admin.getId(), originController.getId())));
    }

    @Test
    public void jwtHttpContractsAndServerOwnedFieldsAreEnforced() throws Exception {
        String auth = token(pilot);
        String route = "/pilot/flights/" + flight.getId();
        mvc.perform(post(route + "/requestTakeOff").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0,\"actorUserId\":999,\"status\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clearance.status").value("PENDING"))
                .andExpect(jsonPath("$.eventId").isNumber()).andExpect(jsonPath("$.clearance.revision").value(1));
        String towerAuth = token(originController);
        mvc.perform(post("/atc/flights/" + flight.getId() + "/clearance/decision").header("Authorization", towerAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"operation\":\"DEPARTURE\",\"decision\":\"APPROVED\",\"expectedRevision\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clearance.status").value("APPROVED"))
                .andExpect(jsonPath("$.clearance.approvedAt").value(org.hamcrest.Matchers.endsWith("Z")));
        mvc.perform(post(route + "/requestLanding").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route + "/clearance/withdraw").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"DEPARTURE\",\"expectedRevision\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(post(route + "/requestTakeOff").contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isUnauthorized());
        var api = jsonMapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Assert.assertTrue(api.at("/paths/~1pilot~1flights~1{flightId}~1requestTakeOff/post/responses").has("201"));
        assertWrites(2);
    }

    @Test
    public void remainingHttpCommandsAndScopeErrorsMatchContracts() throws Exception {
        String pilotAuth = token(secondary);
        String towerAuth = token(originController);
        String route = "/atc/flights/" + flight.getId() + "/clearance";
        mvc.perform(post(route + "/direct").header("Authorization", towerAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"DEPARTURE\",\"expectedRevision\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clearance.cycle").value(1));
        mvc.perform(post(route + "/revoke").header("Authorization", towerAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"DEPARTURE\",\"expectedRevision\":1,\"reason\":\"Traffic\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clearance.status").value("REVOKED"));
        mvc.perform(post("/pilot/flights/" + flight.getId() + "/requestTakeOff").header("Authorization", pilotAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":2}" )).andExpect(status().isCreated());
        mvc.perform(post("/pilot/flights/" + flight.getId() + "/clearance/withdraw").header("Authorization", pilotAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"DEPARTURE\",\"expectedRevision\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clearance.status").value("WITHDRAWN"));
        mvc.perform(post(route + "/direct").header("Authorization", pilotAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"DEPARTURE\",\"expectedRevision\":4}" )).andExpect(status().isForbidden());
        mvc.perform(post("/pilot/flights/9223372036854775807/requestTakeOff").header("Authorization", pilotAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}" )).andExpect(status().isNotFound());
        assertWrites(4);
    }

    @Test
    public void removedPilotAndWrongOperationCannotMutatePendingClearance() {
        pending(flight, FlightOperation.DEPARTURE, origin);
        em.remove(crew.findByFlightIdAndAirlineEmployeeId(flight.getId(), pilot.getAirlineEmployee().getId()).get());
        em.flush();
        Assert.assertThrows(IllegalEndpoint.class, () -> service.withdrawClearance(flight.getId(), FlightOperation.DEPARTURE, 1L));
        login(secondary);
        Assert.assertThrows(BusinessConflictException.class, () -> service.withdrawClearance(flight.getId(), FlightOperation.ARRIVAL, 1L));
        assertWrites(0);
    }

    @Test
    public void pilotHttpWorkflowConsumesClearanceAndUsesServerTimes() throws Exception {
        String primaryAuth = token(pilot);
        String secondaryAuth = token(secondary);
        String route = "/pilot/flights/" + flight.getId();
        login(originController);
        service.directClearance(flight.getId(), FlightOperation.DEPARTURE, 0L, null);
        mvc.perform(post(route + "/takeOff").header("Authorization", primaryAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1,\"actualDeparture\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_AIR"))
                .andExpect(jsonPath("$.clearance.status").value("CONSUMED"));
        FlightControlEvent departed = events.findHistory(flight.getId(), null, null, 1, 0).get(0);
        Assert.assertEquals(FlightControlEvent.EventType.TAKE_OFF, departed.getEventType());
        Assert.assertEquals(LocalDateTime.ofInstant(departed.getOccurredAt(), ZoneId.of("Asia/Bahrain")), flight.getActualDeparture());
        LocalDateTime departure = flight.getActualDeparture();
        mvc.perform(post(route + "/takeOff").header("Authorization", primaryAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":2}")).andExpect(status().isConflict());
        mvc.perform(post(route + "/land").header("Authorization", secondaryAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":2}")).andExpect(status().isConflict());
        plane.setStatus(Airplane.Status.GROUNDED);
        inspection(FlightInspection.InspectionStatus.FAILED);
        login(destinationAdmin);
        service.directClearance(flight.getId(), FlightOperation.ARRIVAL, 2L, null);
        mvc.perform(post(route + "/land").header("Authorization", secondaryAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.clearance.revision").value(4));
        Assert.assertEquals(departure, flight.getActualDeparture());
        Assert.assertFalse(flight.getActualArrival().isBefore(departure));
        Assert.assertFalse(flights.hasOccupiedAirplane(-1L, plane.getId()));
        Assert.assertFalse(crew.hasOccupiedCrew(-1L, List.of(pilot.getAirlineEmployee().getId())));
        mvc.perform(post(route + "/land").header("Authorization", secondaryAuth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":4}")).andExpect(status().isConflict());
        assertWrites(4);
        Assert.assertEquals(2L, em.createQuery("select count(a) from AuditLog a where a.action in :actions and a.entityId in (select e.id from FlightControlEvent e where e.flight.id = :flight)", Long.class)
                .setParameter("actions", List.of(AuditLog.Action.FLIGHT_TAKE_OFF, AuditLog.Action.FLIGHT_LANDED))
                .setParameter("flight", flight.getId()).getSingleResult().longValue());
    }

    @Test
    public void takeOffRechecksReadinessAndRejectsExactExpiryWithoutWrites() {
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(1200));
        try (var time = org.mockito.Mockito.mockStatic(Instant.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(now);
            Assert.assertThrows(BusinessConflictException.class, () -> service.takeOff(flight.getId(), 2L));
        }
        approve(FlightOperation.DEPARTURE, origin, Instant.now());
        plane.setStatus(Airplane.Status.GROUNDED);
        Assert.assertThrows(BusinessConflictException.class, () -> service.takeOff(flight.getId(), 2L));
        Assert.assertNull(flight.getActualDeparture());
        Assert.assertEquals(ClearanceStatus.APPROVED, flight.getClearanceStatus());
        assertWrites(0);
    }

    @Test
    public void physicalActionHttpRejectsUnassignedPilotAndInvalidRevision() throws Exception {
        approve(FlightOperation.DEPARTURE, origin, Instant.now());
        String route = "/pilot/flights/" + flight.getId() + "/takeOff";
        mvc.perform(post(route).header("Authorization", token(unassigned)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":2}")).andExpect(status().isForbidden());
        mvc.perform(post(route).header("Authorization", token(pilot)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isUnauthorized());
        assertWrites(0);
    }

    @Test
    public void delayedCrewAndActivationRemainAllowedButActualDepartureBlocksMutationsAndCheckout() throws Exception {
        login(admin);
        flight.setScheduledDeparture(LocalDateTime.now().minusHours(3));
        flight.setScheduledArrival(LocalDateTime.now().minusHours(1));
        flight.setStatus(Flight.FlightStatus.PENDING_CREW);
        var request = jsonMapper.readValue("{\"employeeId\":" + unassigned.getAirlineEmployee().getId()
                + ",\"duty\":\"PILOT\"}", com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest.class);
        crewService.addCrewAssignment(flight.getId(), request);
        flightService.activateFlight(flight.getId());
        crewService.removeCrewAssignment(flight.getId(), unassigned.getAirlineEmployee().getId());
        Assert.assertEquals(Flight.FlightStatus.ACTIVE, flight.getStatus());
        flight.setActualDeparture(LocalDateTime.now().minusMinutes(1));
        flight.setScheduledDeparture(LocalDateTime.now().plusDays(1));
        Assert.assertThrows(InvalidInformationException.class, () -> crewService.addCrewAssignment(flight.getId(), request));
        Assert.assertThrows(InvalidInformationException.class, () -> crewService.removeCrewAssignment(flight.getId(), pilot.getAirlineEmployee().getId()));
        Assert.assertThrows(InvalidInformationException.class, () -> bookingService.bookFlight(admin.getId(), flight.getId(), "standard", 1L));
        Booking booking = new Booking();
        booking.setFlight(flight);
        booking.setUserId(admin.getId());
        booking.setBookingRef(UUID.randomUUID().toString());
        booking.setSeatNumber("standard-1");
        booking.setStatus(Booking.BookingStatus.BOOKED);
        em.persist(booking);
        Assert.assertThrows(InvalidInformationException.class, () -> paymentService.preparePayment(booking.getId()));
        flight.setStatus(Flight.FlightStatus.PENDING_CREW);
        Assert.assertThrows(InvalidInformationException.class, () -> flightService.activateFlight(flight.getId()));
    }

    private void assertWrites(long count) {
        em.flush();
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
        em.persist(value);
        return value;
    }

    private Airplane plane(Airline owner) {
        Airplane value = new Airplane();
        value.setAirline(owner);
        value.setRegistrationNumber(UUID.randomUUID().toString());
        value.setStatus(Airplane.Status.ACTIVE);
        em.persist(value);
        return value;
    }

    private ControlFacility tower(AirNavigationServiceProvider provider) {
        Airport airport = new Airport();
        airport.setIataCode(UUID.randomUUID().toString());
        airport.setTimeZone("Asia/Bahrain");
        em.persist(airport);
        ControlFacility value = new ControlFacility();
        value.setName("Test tower");
        value.setAirport(airport);
        value.setProvider(provider);
        em.persist(value);
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
        em.persist(value);
        return value;
    }

    private void assign(Flight value, User user, Duty duty) {
        CrewAssignment assignment = new CrewAssignment();
        assignment.setFlight(value);
        assignment.setAirlineEmployee(user.getAirlineEmployee());
        assignment.setDuty(duty);
        em.persist(assignment);
    }

    private void inspection(FlightInspection.InspectionStatus status) {
        FlightInspection value = new FlightInspection();
        value.setFlight(flight);
        value.setAirport(origin.getAirport());
        value.setStatus(status);
        em.persist(value);
    }

    private User employee(Airline company, AirlineEmployee.AirlineRole role) {
        AirlineEmployee profile = new AirlineEmployee();
        profile.setAirline(company);
        profile.setAirlineRole(role);
        User user = user(profile);
        user.setRole(User.Role.AIRLINE_EMPLOYEE);
        user.setAirlineEmployee(profile);
        users.save(user);
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

    private String token(User user) throws Exception {
        SecurityContextHolder.clearContext();
        String result = mvc.perform(post("/auth/users/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", user.getEmailAddress(), "password", "TestPassword1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + jsonMapper.readTree(result).get("message").asText();
    }
}

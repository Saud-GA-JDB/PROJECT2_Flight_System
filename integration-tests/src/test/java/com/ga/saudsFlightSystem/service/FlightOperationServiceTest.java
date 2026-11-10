package com.ga.saudsFlightSystem.service;

import com.ga.identity.model.User;
import com.ga.identity.model.UserProfile;
import com.ga.identity.repository.UserRepository;
import static com.ga.saudsFlightSystem.support.IdentityFixtures.*;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
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

@org.springframework.context.annotation.Import(com.ga.saudsFlightSystem.support.IdentityTestConfiguration.class)
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
        "stripe.secret-key=sk_test_unused", "stripe.success-url=http://localhost/payments/success",
        "stripe.cancel-url=http://localhost/payments/cancel"
})
@AutoConfigureMockMvc
@Transactional
public class FlightOperationServiceTest {
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
    public final void whenReadyWithoutClearanceThenRequestAndApprovalAreReadyButActionIsNot() {
        ReadinessResponse result = readiness.evaluate(flight, FlightOperation.DEPARTURE, now);
        Assert.assertTrue(result.isReadyForRequest());
        Assert.assertTrue(result.isReadyForApproval());
        Assert.assertFalse(result.isReadyForAction());
        Assert.assertEquals("CLEARANCE_REQUIRED", result.getBlockers().get(0).getCode());
        Assert.assertEquals(List.of("ACTION"), result.getBlockers().get(0).getAppliesTo());
        Assert.assertEquals(now, result.getEvaluatedAt());
        flight.setScheduledDeparture(LocalDateTime.of(2020, 1, 1, 1, 0));
        Assert.assertTrue(readiness.evaluate(flight, FlightOperation.DEPARTURE, now).isReadyForRequest());
    }

    @Test
    public final void whenEitherPilotIsAssignedThenBothCanReadButRemovedAndUnrelatedStaffCannot() {
        Assert.assertEquals(flight.getId(), service.getPilotFlight(flight.getId()).getBody().getFlightId());
        login(secondary);
        Assert.assertEquals(1L, service.getPilotFlights(null, null, null, 0, 20).getBody().getTotalElements());
        CrewAssignment assignment = crew.findByFlightIdAndAirlineEmployeeId(flight.getId(), airlineEmployee(secondary).getId()).get();
        em.remove(assignment);
        em.flush();
        Assert.assertTrue(service.getPilotFlights(null, null, null, 0, 20).getBody().getContent().isEmpty());
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getPilotFlight(flight.getId()));
        login(unassigned);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getPilotReadiness(flight.getId()));
        login(attendant);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getPilotFlights(null, null, null, 0, 20));
        login(admin);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getPilotFlight(flight.getId()));
        Assert.assertEquals(flight.getId(), service.getAirlineFlight(flight.getId()).getBody().getFlightId());
        login(foreignAdmin);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getAirlineFlight(flight.getId()));
    }

    @Test
    public final void whenTowerReadsFlightThenAirportAndOperationScopeAreIndependentOfProvider() {
        login(originController);
        Assert.assertEquals(flight.getId(), service.getTowerFlight(flight.getId()).getBody().getFlightId());
        Assert.assertEquals(1L, service.getTowerFlights(FlightOperation.DEPARTURE, null, null, null, 0, 20).getBody().getTotalElements());
        Assert.assertEquals(0L, service.getTowerFlights(FlightOperation.ARRIVAL, null, null, null, 0, 20).getBody().getTotalElements());
        Assert.assertTrue(service.getTowerReadiness(flight.getId(), FlightOperation.DEPARTURE).getBody().isReadyForRequest());
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getTowerReadiness(flight.getId(), FlightOperation.ARRIVAL));
        login(destinationAdmin);
        Assert.assertEquals(flight.getId(), service.getTowerFlight(flight.getId()).getBody().getFlightId());
        Assert.assertFalse(service.getTowerReadiness(flight.getId(), FlightOperation.ARRIVAL).getBody().isReadyForRequest());
        login(otherController);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getTowerFlight(flight.getId()));
        Assert.assertTrue(service.getTowerFlights(null, null, null, null, 0, 20).getBody().getContent().isEmpty());
    }

    @Test
    public final void whenPagingAndDatesAreAppliedThenCountsStayScopedAndOrderingIsStable() {
        Flight second = flight(plane, origin, destination);
        second.setScheduledDeparture(flight.getScheduledDeparture());
        assign(second, pilot, Duty.SECONDARY_PILOT);
        Flight hidden = flight(plane, other, destination);
        hidden.setScheduledDeparture(flight.getScheduledDeparture());
        em.flush();
        LocalDate date = flight.getScheduledDeparture().toLocalDate();
        FlightOperationListResponse firstPage = service.getPilotFlights(Flight.FlightStatus.ACTIVE, date, date, 0, 1).getBody();
        Assert.assertEquals(2L, firstPage.getTotalElements());
        Assert.assertEquals(2, firstPage.getTotalPages());
        Assert.assertEquals(flight.getId(), firstPage.getContent().get(0).getFlightId());
        Assert.assertEquals(second.getId(), service.getPilotFlights(null, date, date, 1, 1).getBody().getContent().get(0).getFlightId());
        Assert.assertTrue(service.getPilotFlights(null, date.plusDays(1), null, 0, 20).getBody().getContent().isEmpty());
        Assert.assertTrue(service.getPilotFlights(Flight.FlightStatus.CLOSED, null, null, 0, 20).getBody().getContent().isEmpty());
        Assert.assertThrows(InvalidInformationException.class, () -> service.getPilotFlights(null, date.plusDays(1), date, 0, 20));
        Assert.assertThrows(InvalidInformationException.class, () -> service.getPilotFlights(null, null, null, 0, 101));
        login(originController);
        Assert.assertEquals(2L, service.getTowerFlights(null, null, date, date, 0, 1).getBody().getTotalElements());
    }

    @Test
    public final void whenClearanceExpiresThenBoundaryReadsNeverWriteStateOrEvents() {
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(1200));
        em.flush();
        long auditCount = em.createQuery("select count(a) from AuditLog a", Long.class).getSingleResult();
        Assert.assertTrue(readiness.evaluate(flight, FlightOperation.DEPARTURE, now.minusNanos(1)).isReadyForAction());
        Assert.assertFalse(readiness.evaluate(flight, FlightOperation.DEPARTURE, now.minusNanos(1)).isReadyForApproval());
        ReadinessResponse expired = readiness.evaluate(flight, FlightOperation.DEPARTURE, now);
        Assert.assertTrue(expired.isReadyForRequest());
        Assert.assertTrue(expired.isReadyForApproval());
        Assert.assertFalse(expired.isReadyForAction());
        Assert.assertTrue(has(expired, "CLEARANCE_EXPIRED"));
        Assert.assertEquals("EXPIRED", readiness.effectiveClearanceStatus(flight, now));
        Assert.assertEquals("EXPIRED", readiness.effectiveClearanceStatus(flight, now.plusSeconds(1)));
        em.flush();
        em.clear();
        Assert.assertEquals(ClearanceStatus.APPROVED, flights.findById(flight.getId()).get().getClearanceStatus());
        Assert.assertEquals(2L, flights.findById(flight.getId()).get().getClearanceRevision());
        Assert.assertEquals(0, events.count());
        Assert.assertEquals(auditCount, em.createQuery("select count(a) from AuditLog a", Long.class).getSingleResult().longValue());
    }

    @Test
    public final void whenPendingThenItDoesNotExpireAndRequestsAreScopedToTheirTower() {
        pending(flight, FlightOperation.DEPARTURE, origin);
        Flight another = flight(plane, destination, other);
        pending(another, FlightOperation.DEPARTURE, destination);
        em.flush();
        ReadinessResponse result = readiness.evaluate(flight, FlightOperation.DEPARTURE, now.plusSeconds(86400));
        Assert.assertFalse(result.isReadyForRequest());
        Assert.assertTrue(result.isReadyForApproval());
        Assert.assertTrue(has(result, "REQUEST_ALREADY_PENDING"));
        login(originController);
        Assert.assertEquals(1L, service.getTowerRequests(null, 0, 20).getBody().getTotalElements());
        Assert.assertEquals(0L, service.getTowerRequests(FlightOperation.ARRIVAL, 0, 20).getBody().getTotalElements());
        login(destinationAdmin);
        Assert.assertEquals(another.getId(), service.getTowerRequests(null, 0, 20).getBody().getContent().get(0).getFlightId());
    }

    @Test
    public final void whenNewestInspectionFailsThenAnOlderPassDoesNotClearReadiness() {
        inspection(FlightInspection.InspectionStatus.FAILED);
        em.flush();
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "INSPECTION_NOT_PASSED"));
        Assert.assertEquals(FlightInspection.InspectionStatus.FAILED, service.getPilotFlight(flight.getId()).getBody().getLatestInspection().getStatus());
        inspection(FlightInspection.InspectionStatus.PENDING);
        em.flush();
        Assert.assertFalse(readiness.evaluate(flight, FlightOperation.DEPARTURE, now).isReadyForRequest());
        for (FlightInspection value : inspections.findByFlight_IdOrderByIdDesc(flight.getId())) em.remove(value);
        em.flush();
        Assert.assertNull(service.getPilotFlight(flight.getId()).getBody().getLatestInspection());
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "INSPECTION_NOT_PASSED"));
    }

    @Test
    public final void whenEachRequiredDutyIsMissingThenReadinessBlocksAllStages() {
        for (CrewAssignment assignment : crew.findByFlightId(flight.getId())) {
            Duty original = assignment.getDuty();
            assignment.setDuty(null);
            em.flush();
            ReadinessResponse result = readiness.evaluate(flight, FlightOperation.DEPARTURE, now);
            Assert.assertTrue(has(result, "CREW_INCOMPLETE"));
            Assert.assertFalse(result.isReadyForRequest());
            Assert.assertFalse(result.isReadyForApproval());
            Assert.assertFalse(result.isReadyForAction());
            assignment.setDuty(original);
        }
    }

    @Test
    public final void whenResourcesAreOccupiedThenOldSchedulesAndExtraCrewDoNotFreeThem() {
        Flight occupied = flight(plane, other, destination);
        occupied.setScheduledDeparture(LocalDateTime.of(2020, 1, 1, 0, 0));
        occupied.setScheduledArrival(occupied.getScheduledDeparture().plusHours(2));
        occupied.setActualDeparture(LocalDateTime.of(2026, 11, 7, 8, 0));
        assign(flight, unassigned, Duty.PILOT);
        assign(occupied, unassigned, Duty.PILOT);
        em.flush();
        ReadinessResponse result = readiness.evaluate(flight, FlightOperation.DEPARTURE, now);
        Assert.assertTrue(has(result, "AIRPLANE_IN_USE"));
        Assert.assertTrue(has(result, "CREW_IN_USE"));
        occupied.setActualArrival(LocalDateTime.of(2026, 11, 7, 9, 0));
        occupied.setStatus(Flight.FlightStatus.CLOSED);
        em.flush();
        Assert.assertTrue(readiness.evaluate(flight, FlightOperation.DEPARTURE, now).isReadyForRequest());
        occupied.setStatus(Flight.FlightStatus.IN_AIR);
        em.flush();
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "AIRPLANE_IN_USE"));
    }

    @Test
    public final void whenAirborneThenArrivalNeedsNoFreshInspectionOrActiveAirplaneOrFreeResources() {
        flight.setStatus(Flight.FlightStatus.IN_AIR);
        flight.setActualDeparture(LocalDateTime.of(2026, 11, 7, 9, 0));
        plane.setStatus(Airplane.Status.GROUNDED);
        inspection(FlightInspection.InspectionStatus.FAILED);
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(1800));
        flight.setClearanceStatus(ClearanceStatus.CONSUMED);
        em.flush();
        ReadinessResponse result = readiness.evaluate(flight, FlightOperation.ARRIVAL, now);
        Assert.assertTrue(result.isReadyForRequest());
        Assert.assertTrue(result.isReadyForApproval());
        Assert.assertFalse(result.isReadyForAction());
        approve(FlightOperation.ARRIVAL, destination, now.minusSeconds(5));
        Assert.assertTrue(readiness.evaluate(flight, FlightOperation.ARRIVAL, now).isReadyForAction());
        flight.setActualDeparture(LocalDateTime.of(2026, 11, 7, 10, 0, 1));
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.ARRIVAL, now), "ACTUAL_TIME_CONFLICT"));
    }

    @Test
    public final void whenWrongOperationOrTowerIsApprovedThenPhysicalActionIsBlocked() {
        approve(FlightOperation.ARRIVAL, origin, now.minusSeconds(10));
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "CLEARANCE_OPERATION_MISMATCH"));
        approve(FlightOperation.DEPARTURE, destination, now.minusSeconds(10));
        Assert.assertFalse(readiness.evaluate(flight, FlightOperation.DEPARTURE, now).isReadyForAction());
    }

    @Test
    public final void whenReadinessChangesAfterApprovalThenApprovalIsRetainedButActionIsBlocked() {
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(10));
        Instant expires = flight.getClearanceExpiresAt();
        plane.setStatus(Airplane.Status.GROUNDED);
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "AIRPLANE_NOT_ACTIVE"));
        Assert.assertEquals(ClearanceStatus.APPROVED, flight.getClearanceStatus());
        Assert.assertEquals(expires, flight.getClearanceExpiresAt());
        flight.setActualDeparture(LocalDateTime.of(2026, 11, 7, 9, 0));
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "ACTUAL_TIME_CONFLICT"));
    }

    @Test
    public final void whenFlightIsTerminalThenPilotReadinessHasNoOperationAndAllActionsAreBlocked() {
        for (Flight.FlightStatus status : List.of(Flight.FlightStatus.CLOSED, Flight.FlightStatus.CANCELLED)) {
            flight.setStatus(status);
            ReadinessResponse result = service.getPilotReadiness(flight.getId()).getBody();
            Assert.assertNull(result.getOperation());
            Assert.assertFalse(result.isReadyForRequest());
            Assert.assertFalse(result.isReadyForApproval());
            Assert.assertFalse(result.isReadyForAction());
        }
    }

    @Test
    public final void whenTowerIsMissingThenReadinessExplainsTheBlock() {
        Airport unconfigured = new Airport();
        unconfigured.setIataCode(UUID.randomUUID().toString());
        em.persist(unconfigured);
        flight.setOriginAirport(unconfigured);
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.DEPARTURE, now), "TOWER_NOT_CONFIGURED"));
        flight.setOriginAirport(origin.getAirport());
        flight.setDestinationAirport(unconfigured);
        Assert.assertTrue(readiness.evaluate(flight, FlightOperation.DEPARTURE, now).isReadyForRequest());
    }

    @Test
    public final void whenHistoryIsReadThenTowerSeesOnlyOwnEventsAndAirlineSeesBoth() {
        FlightControlEvent first = event(origin, FlightOperation.DEPARTURE, 1);
        FlightControlEvent second = event(destination, FlightOperation.ARRIVAL, 2);
        em.flush();
        Assert.assertEquals(second.getId(), service.getPilotEvents(flight.getId(), null, 0, 1).getBody().getContent().get(0).getId());
        Assert.assertEquals(2L, service.getPilotEvents(flight.getId(), null, 0, 1).getBody().getTotalElements());
        login(originController);
        ControlEventListResponse result = service.getTowerEvents(flight.getId(), null, 0, 1).getBody();
        Assert.assertEquals(1L, result.getTotalElements());
        Assert.assertEquals(first.getId(), result.getContent().get(0).getId());
        Assert.assertEquals(0L, service.getTowerEvents(flight.getId(), FlightOperation.ARRIVAL, 0, 20).getBody().getTotalElements());
        login(admin);
        Assert.assertEquals(2L, service.getAirlineEvents(flight.getId(), null, 0, 20).getBody().getTotalElements());
        login(foreignAdmin);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getAirlineEvents(flight.getId(), null, 0, 20));
        login(otherController);
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getTowerEvents(flight.getId(), null, 0, 20));
    }

    @Test
    public final void whenAccountIsInactiveThenServicesDenyAccessEvenWithAnExistingPrincipal() {
        pilot.setStatus(User.Status.DEACTIVATED);
        em.flush();
        Assert.assertThrows(IllegalEndpoint.class, () -> service.getPilotFlight(flight.getId()));
    }

    @Test
    public final void whenHTTPReadsAreCalledThenJWTScopesValidationAndSafeJSONAreEnforced() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/pilot/flights")).andExpect(status().isUnauthorized());
        String pilotToken = token(pilot);
        mvc.perform(get("/pilot/flights/" + flight.getId()).header("Authorization", pilotToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clearance.status").value("NONE"))
                .andExpect(jsonPath("$.crew.length()").value(3)).andExpect(jsonPath("$.bookingsList").doesNotExist())
                .andExpect(jsonPath("$.standardPrice").doesNotExist()).andExpect(jsonPath("$.crew[0].cpr").doesNotExist());
        mvc.perform(get("/pilot/flights/" + flight.getId() + "/readiness").header("Authorization", pilotToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readyForRequest").value(true))
                .andExpect(jsonPath("$.readyForAction").value(false));
        mvc.perform(get("/pilot/flights").header("Authorization", pilotToken).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/pilot/flights").header("Authorization", pilotToken).param("fromDate", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/pilot/flights").header("Authorization", pilotToken).param("status", "unknown")).andExpect(status().isBadRequest());
        mvc.perform(get("/pilot/flights/0").header("Authorization", pilotToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/pilot/flights/9223372036854775807").header("Authorization", pilotToken)).andExpect(status().isNotFound());
        String towerToken = token(originController);
        mvc.perform(get("/atc/flights/" + flight.getId() + "/readiness").header("Authorization", towerToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/atc/flights/" + flight.getId() + "/readiness").header("Authorization", towerToken).param("operation", "ARRIVAL"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/atc/flights").header("Authorization", towerToken).param("operation", "DEPARTURE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/atc/requests").header("Authorization", towerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/atc/flights/" + flight.getId() + "/controlEvents").header("Authorization", towerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/airlineAdmin/flights/" + flight.getId() + "/operations").header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.flightId").value(flight.getId()));
    }

    @Test
    public final void whenRejectedWithdrawnOrRevokedThenFreshRequestIsEligibleButOldPermissionCannotBeUsed() {
        for (ClearanceStatus status : List.of(ClearanceStatus.REJECTED, ClearanceStatus.WITHDRAWN, ClearanceStatus.REVOKED)) {
            if (status == ClearanceStatus.REVOKED)
                approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(10));
            else
                pending(flight, FlightOperation.DEPARTURE, origin);
            flight.setClearanceStatus(status);
            em.flush();
            ReadinessResponse result = readiness.evaluate(flight, FlightOperation.DEPARTURE, now);
            Assert.assertTrue(result.isReadyForRequest());
            Assert.assertTrue(result.isReadyForApproval());
            Assert.assertFalse(result.isReadyForAction());
        }
    }

    @Test
    public final void whenActualTimesAreInconsistentThenArrivalRemainsBlocked() {
        flight.setStatus(Flight.FlightStatus.IN_AIR);
        Assert.assertTrue(has(readiness.evaluate(flight, FlightOperation.ARRIVAL, now), "ACTUAL_TIME_CONFLICT"));
        flight.setActualDeparture(LocalDateTime.ofInstant(now, ZoneId.of("Asia/Bahrain")));
        Assert.assertTrue(readiness.evaluate(flight, FlightOperation.ARRIVAL, now).isReadyForRequest());
        flight.setActualArrival(flight.getActualDeparture().minusMinutes(1));
        Assert.assertFalse(readiness.evaluate(flight, FlightOperation.ARRIVAL, now).isReadyForRequest());
    }

    @Test
    public final void whenUTCFieldsAreSerializedThenJSONUsesZAndSwaggerExposesReadModels() throws Exception {
        approve(FlightOperation.DEPARTURE, origin, now.minusSeconds(1200));
        event(origin, FlightOperation.DEPARTURE, 1);
        em.flush();
        String auth = token(pilot);
        mvc.perform(get("/pilot/flights/" + flight.getId()).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clearance.expiresAt").value("2026-11-07T07:00:00Z"));
        mvc.perform(get("/pilot/flights/" + flight.getId() + "/controlEvents").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].occurredAt").value("2026-11-07T07:00:01Z"));
        var api = jsonMapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        Assert.assertTrue(api.at("/components/schemas/ReadinessResponse/properties").has("blockers"));
        Assert.assertTrue(api.at("/components/schemas/FlightOperationSummary/properties").has("clearance"));
        Assert.assertTrue(api.at("/paths/~1atc~1requests/get/responses").has("200"));
    }

    @Test
    public final void whenStoredRevisionIsNegativeThenDatabaseRejectsIt() {
        flight.setClearanceRevision(-1);
        Assert.assertThrows(DataIntegrityViolationException.class, () -> flights.saveAndFlush(flight));
    }

    @Test
    public final void whenEventRevisionRepeatsThenDatabaseRejectsIt() {
        event(origin, FlightOperation.DEPARTURE, 1);
        em.flush();
        FlightControlEvent duplicate = new FlightControlEvent();
        duplicate.setFlight(flight);
        duplicate.setFacility(origin);
        duplicate.setActorUserId(pilot.getId());
        duplicate.setOperation(FlightOperation.DEPARTURE);
        duplicate.setEventType(FlightControlEvent.EventType.APPROVED);
        duplicate.setCycle(1);
        duplicate.setRevision(1);
        duplicate.setOccurredAt(now);
        Assert.assertThrows(DataIntegrityViolationException.class, () -> events.saveAndFlush(duplicate));
    }

    private boolean has(ReadinessResponse response, String code) {
        return response.getBlockers().stream().anyMatch(b -> b.getCode().equals(code));
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

    private FlightControlEvent event(ControlFacility tower, FlightOperation operation, long revision) {
        FlightControlEvent event = new FlightControlEvent();
        event.setFlight(flight);
        event.setFacility(tower);
        event.setActorUserId(originController.getId());
        event.setOperation(operation);
        event.setEventType(FlightControlEvent.EventType.REQUESTED);
        event.setCycle(revision);
        event.setRevision(revision);
        event.setOccurredAt(now.plusSeconds(revision));
        em.persist(event);
        return event;
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
        assignment.setAirlineEmployee(airlineEmployee(user));
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
        User user = user();
        user.setRole(User.Role.AIRLINE_EMPLOYEE);
        linkEmployee(user, profile);
        users.save(user);
        profile.setUserId(user.getId());
        return user;
    }

    private User atc(ControlFacility tower, ATCEmployee.ATCRole role) {
        ATCEmployee profile = new ATCEmployee();
        profile.setFacility(tower);
        profile.setAtcRole(role);
        profile.setEmployeeNumber(UUID.randomUUID().toString().toUpperCase());
        User user = user();
        user.setRole(User.Role.ATC_EMPLOYEE);
        linkEmployee(user, profile);
        users.save(user);
        profile.setUserId(user.getId());
        return user;
    }

    private User user() {
        User user = new User();
        user.setEmailAddress(UUID.randomUUID() + "@example.com");
        user.setPassword(encoded);
        user.setActive(true);
        user.setStatus(User.Status.ACTIVE);
        user.setPersonalProfile(new UserProfile(
                "Test", "Operations",
                String.valueOf(ThreadLocalRandom.current().nextLong(100000000, 1000000000)),
                null, null));
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

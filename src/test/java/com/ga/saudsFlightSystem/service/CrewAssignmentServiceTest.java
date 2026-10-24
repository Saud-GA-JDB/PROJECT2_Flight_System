package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest;
import com.ga.saudsFlightSystem.model.request.response.CrewAssignmentResponse;
import com.ga.saudsFlightSystem.model.request.response.getCrewAssignmentsResponse;
import com.ga.saudsFlightSystem.repository.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class CrewAssignmentServiceTest {
    @Autowired
    CrewAssignmentService crewAssignmentService;
    @Autowired
    CrewAssignmentRepository crewAssignmentRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    AirlineEmployeeRepository airlineEmployeeRepository;
    @Autowired
    FlightRepository flightRepository;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JsonMapper jsonMapper;

    Flight flight;
    AirlineEmployee pilot;
    AddCrewAssignmentRequest request;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "flighttest.airline@mailsac.com", "TestPassword1")));

        AirlineEmployee admin = UserService.getCurrentLoggedInUser().getAirlineEmployee();
        Assert.assertEquals(AirlineEmployee.AirlineRole.ADMIN, admin.getAirlineRole());

        pilot = new AirlineEmployee();
        pilot.setCpr("999888777");
        pilot.setFName("Test");
        pilot.setLName("Pilot");
        pilot.setAirlineRole(AirlineEmployee.AirlineRole.PILOT);
        pilot.setAirline(admin.getAirline());
        airlineEmployeeRepository.save(pilot);

        flight = new Flight();
        flight.setFlightNumber("TEST-CREW");
        flight.setAirline(admin.getAirline());
        flight.setStatus(Flight.FlightStatus.ACTIVE);
        flight.setScheduledDeparture(LocalDateTime.now().plusDays(7));
        flight.setScheduledArrival(flight.getScheduledDeparture().plusHours(2));
        flightRepository.save(flight);

        request = jsonMapper.readValue(
                "{\"employeeId\":" + pilot.getId() + ",\"duty\":\"PILOT\"}",
                AddCrewAssignmentRequest.class);
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("when admin manages crew then assignment is added listed and removed")
    public final void whenAdminManagesCrewThenAssignmentIsAddedListedAndRemoved() {
        ResponseEntity<?> added = crewAssignmentService.addCrewAssignment(
                flight.getId(), request);
        CrewAssignmentResponse assignment = (CrewAssignmentResponse) added.getBody();

        Assert.assertEquals(HttpStatus.CREATED, added.getStatusCode());
        Assert.assertNotNull(assignment);
        Assert.assertEquals(pilot.getId(), assignment.getEmployeeId());
        Assert.assertTrue(crewAssignmentRepository.existsById(assignment.getId()));
        assertAuditLogged(AuditLog.Action.CREW_ASSIGNMENT_CREATED,
                AuditLog.EntityType.CREW_ASSIGNMENT, assignment.getId());

        ResponseEntity<?> listed = crewAssignmentService.getCrewAssignments(flight.getId());
        List<?> assignments = (List<?>) listed.getBody();

        Assert.assertEquals(HttpStatus.OK, listed.getStatusCode());
        Assert.assertNotNull(assignments);
        Assert.assertEquals(1, assignments.size());
        getCrewAssignmentsResponse crew = (getCrewAssignmentsResponse) assignments.get(0);
        Assert.assertEquals(pilot.getId(), crew.getEmployeeId());
        Assert.assertEquals(Duty.PILOT, crew.getDuty());
        assertAuditLogged(AuditLog.Action.CREW_ASSIGNMENTS_VIEWED,
                AuditLog.EntityType.FLIGHT, flight.getId());

        ResponseEntity<?> removed = crewAssignmentService.removeCrewAssignment(
                flight.getId(), pilot.getId());

        Assert.assertEquals(HttpStatus.NO_CONTENT, removed.getStatusCode());
        Assert.assertFalse(crewAssignmentRepository.existsById(assignment.getId()));
        assertAuditLogged(AuditLog.Action.CREW_ASSIGNMENT_REMOVED,
                AuditLog.EntityType.CREW_ASSIGNMENT, assignment.getId());
    }

    private void assertAuditLogged(AuditLog.Action action, AuditLog.EntityType entityType, Long entityId) {
        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == action && log.getEntityType() == entityType
                        && log.getEntityId().equals(entityId))
                .toList();

        Assert.assertEquals(1, logs.size());
        Assert.assertEquals(UserService.getCurrentLoggedInUser().getId(), logs.get(0).getUserId());
        Assert.assertEquals(User.Role.AIRLINE_EMPLOYEE, logs.get(0).getUserRole());
        Assert.assertNotNull(logs.get(0).getCreatedAt());
        Assert.assertFalse(logs.get(0).getDescription().isBlank());
    }

    @Test(expected = InvalidInformationException.class)
    @DisplayName("when pilot has overlapping flight then assignment is rejected")
    public final void whenPilotHasOverlappingFlightThenAssignmentIsRejected() {
        crewAssignmentService.addCrewAssignment(flight.getId(), request);

        Flight overlappingFlight = new Flight();
        overlappingFlight.setFlightNumber("TEST-CREW-OVERLAP");
        overlappingFlight.setAirline(flight.getAirline());
        overlappingFlight.setStatus(Flight.FlightStatus.ACTIVE);
        overlappingFlight.setScheduledDeparture(
                flight.getScheduledDeparture().plusHours(1));
        overlappingFlight.setScheduledArrival(
                flight.getScheduledArrival().plusHours(1));
        flightRepository.save(overlappingFlight);

        crewAssignmentService.addCrewAssignment(overlappingFlight.getId(), request);
    }

    @Test(expected = InvalidInformationException.class)
    @DisplayName("when pilot is assigned attendant duty then assignment is rejected")
    public final void whenPilotIsAssignedAttendantDutyThenAssignmentIsRejected() {
        AddCrewAssignmentRequest invalidRequest = jsonMapper.readValue(
                "{\"employeeId\":" + pilot.getId() + ",\"duty\":\"FLIGHT_ATTENDANT\"}",
                AddCrewAssignmentRequest.class);

        crewAssignmentService.addCrewAssignment(flight.getId(), invalidRequest);
    }

    @Test(expected = IllegalEndpoint.class)
    @DisplayName("when customer assigns crew then access is denied")
    public final void whenCustomerAssignsCrewThenAccessIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "flighttest.customer@mailsac.com", "TestPassword1")));

        crewAssignmentService.addCrewAssignment(flight.getId(), request);
    }
}

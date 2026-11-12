package com.ga.saudsFlightSystem.service;

import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.saudsFlightSystem.client.NotificationServiceClient;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.identity.PersonalProfileStore;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest;
import com.ga.saudsFlightSystem.model.request.EmailRequest;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.CrewAssignmentRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrewEmailExtractionTest {
    private final NotificationServiceClient client = mock(NotificationServiceClient.class);
    private final NotificationService notifications = new NotificationService(client);
    private final CrewAssignmentRepository assignments = mock(CrewAssignmentRepository.class);
    private final AirlineEmployeeRepository employees = mock(AirlineEmployeeRepository.class);
    private final FlightRepository flights = mock(FlightRepository.class);
    private final AuditLogService audits = mock(AuditLogService.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AccountLookup accounts = mock(AccountLookup.class);
    private final PersonalProfileStore profiles = mock(PersonalProfileStore.class);
    private final CrewAssignmentService service = new CrewAssignmentService(assignments, employees, flights, audits, notifications, actors, accounts, profiles);
    private final AddCrewAssignmentRequest request = new AddCrewAssignmentRequest();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(notifications, "serviceKey", "test-key");
        ReflectionTestUtils.setField(request, "employeeId", 8L);
        ReflectionTestUtils.setField(request, "duty", Duty.PILOT);
        Airline airline = new Airline();
        airline.setId(1L);
        AirlineEmployee admin = new AirlineEmployee();
        admin.setAirline(airline);
        AirlineEmployee pilot = new AirlineEmployee();
        pilot.setId(8L);
        pilot.setUserId(80L);
        pilot.setAirline(airline);
        pilot.setAirlineRole(AirlineEmployee.AirlineRole.PILOT);
        Flight flight = new Flight();
        flight.setId(5L);
        flight.setFlightNumber("TEST5");
        flight.setAirline(airline);
        flight.setStatus(Flight.FlightStatus.PENDING_CREW);
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(10L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(employees.findByUserIdAndRole(10L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.of(admin));
        when(employees.findByIdForAssignment(8L)).thenReturn(Optional.of(pilot));
        when(employees.findUserIdByEmployeeId(8L)).thenReturn(Optional.of(80L));
        when(flights.findByIdForCrewWrite(5L)).thenReturn(Optional.of(flight));
        when(accounts.findEmailAddressById(80L)).thenReturn(Optional.of("pilot@example.com"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void crewUsesLinkedUserEmailAndAsyncEndpointAfterCommit() {
        service.addCrewAssignment(5L, request);
        verifyNoInteractions(client);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(client).sendAsyncEmail("test-key", new EmailRequest("pilot@example.com", "New Crew Assignment", "You have been assigned to flight TEST5 with duty PILOT"));
        verify(accounts, never()).findEmailAddressById(8L);
        verify(client, never()).sendEmail(anyString(), any());
    }

    @Test
    void auditFailureDoesNotSendQueuedCrewEmail() {
        doThrow(new IllegalStateException("audit failed")).when(audits).addAuditLogForActor(any(), any(), any(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> service.addCrewAssignment(5L, request));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(client);
    }
}

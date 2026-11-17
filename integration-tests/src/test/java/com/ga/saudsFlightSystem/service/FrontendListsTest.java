package com.ga.saudsFlightSystem.service;

import com.ga.common.contract.identity.AccountSnapshot;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.identity.PersonalProfile;
import com.ga.saudsFlightSystem.identity.PersonalProfileStore;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirportEmployeeRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Tests service authorization and response mapping with mocked persistence and Identity.
class FrontendListsTest {
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AirlineEmployeeRepository employees = mock(AirlineEmployeeRepository.class);
    private final FlightRepository flights = mock(FlightRepository.class);
    private final AccountLookup accounts = mock(AccountLookup.class);
    private final PersonalProfileStore profiles = mock(PersonalProfileStore.class);
    private final AirlineEmployee admin = new AirlineEmployee();
    private final FlightService flightService = new FlightService(flights, null, null, null, actors, employees, null);
    private final AirlineEmployeeService employeeService = new AirlineEmployeeService(
            null, null, employees, null, null, null, null, null, null, null, null, actors, accounts, profiles);

    @BeforeEach
    void setup() {
        Airline airline = new Airline();
        airline.setId(6L);
        admin.setAirline(airline);
        admin.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.of(admin));
    }

    @Test
    void historicalFlightPageUsesCallerAirlineAndInclusiveDates() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(flights.findAirlineFlightIds(6L, "CLOSED", from.atStartOfDay(), to.plusDays(1).atStartOfDay(), 20, 20L))
                .thenReturn(List.of(8L));
        when(flights.countAirlineFlights(6L, "CLOSED", from.atStartOfDay(), to.plusDays(1).atStartOfDay())).thenReturn(21L);
        Flight flight = new Flight();
        flight.setId(8L);
        flight.setStatus(Flight.FlightStatus.CLOSED);
        when(flights.findAirlineFlightsWithDetails(6L, List.of(8L))).thenReturn(List.of(flight));

        var response = flightService.getAirlineFlights(Flight.FlightStatus.CLOSED, from, to, 1, 20);
        assertEquals(21L, response.getTotalElements());
        assertEquals(2L, response.getTotalPages());
        assertEquals(6L, response.getContent().get(0).getAirlineId());
        assertEquals(Flight.FlightStatus.CLOSED, response.getContent().get(0).getStatus());
        assertNull(response.getContent().get(0).getAirplaneId());
    }

    @Test
    void emptyPageDoesNotRunAnEmptyInQuery() {
        when(flights.findAirlineFlightIds(6L, null, null, null, 20, 0L)).thenReturn(List.of());
        var response = flightService.getAirlineFlights(null, null, null, 0, 20);
        assertTrue(response.getContent().isEmpty());
        assertEquals(0L, response.getTotalPages());
        verify(flights, never()).findAirlineFlightsWithDetails(anyLong(), anyList());
    }

    @Test
    void invalidDatesAndPaginationFailBeforeFlightQueries() {
        assertThrows(InvalidInformationException.class, () -> flightService.getAirlineFlights(null, null, null, -1, 20));
        assertThrows(InvalidInformationException.class, () -> flightService.getAirlineFlights(null, null, null, 0, 101));
        assertThrows(InvalidInformationException.class, () -> flightService.getAirlineFlights(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), 0, 20));
        verifyNoInteractions(flights);
    }

    @Test
    void customerAndNonAdminCannotListFlights() {
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "CUSTOMER", "ACTIVE", true));
        assertThrows(IllegalEndpoint.class, () -> flightService.getAirlineFlights(null, null, null, 0, 20));
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.empty());
        assertThrows(IllegalEndpoint.class, () -> flightService.getAirlineFlights(null, null, null, 0, 20));
        verifyNoInteractions(flights);
    }

    @Test
    void crewSelectorKeepsEmployeeAndAccountIdsSeparateAndExcludesInactiveAccounts() {
        AirlineEmployee pilot = new AirlineEmployee();
        pilot.setId(30L);
        pilot.setUserId(400L);
        pilot.setAirlineRole(AirlineEmployee.AirlineRole.PILOT);
        AirlineEmployee inactive = new AirlineEmployee();
        inactive.setId(31L);
        inactive.setUserId(401L);
        when(employees.findEmployeesByAirlineId(6L, AirlineEmployee.AirlineRole.PILOT)).thenReturn(List.of(pilot, inactive));
        when(accounts.findAccountsByIds(List.of(400L, 401L))).thenReturn(Map.of(
                400L, new AccountSnapshot(400L, "AIRLINE_EMPLOYEE", "ACTIVE", true, "pilot@example.com"),
                401L, new AccountSnapshot(401L, "AIRLINE_EMPLOYEE", "DEACTIVATED", false, "old@example.com")));
        when(profiles.findByUserIds(List.of(400L, 401L))).thenReturn(Map.of(
                400L, new PersonalProfile("Ahmed", "Ali", null, null, null)));
        var response = employeeService.getAirlineEmployees(AirlineEmployee.AirlineRole.PILOT);
        assertEquals(1, response.size());
        assertEquals(30L, response.get(0).getId());
        assertEquals(400L, response.get(0).getUserId());
        assertEquals("Ahmed", response.get(0).getFName());
    }

    @Test
    void airportAdminFromAnOldOperatorCannotListEmployees() {
        AirportEmployeeRepository airportEmployees = mock(AirportEmployeeRepository.class);
        AirportEmployeeService service = new AirportEmployeeService(actors, null, null, airportEmployees, null, null, null, accounts, profiles);
        AirportOperator oldOperator = new AirportOperator();
        oldOperator.setId(10L);
        AirportOperator currentOperator = new AirportOperator();
        currentOperator.setId(11L);
        Airport airport = new Airport();
        airport.setId(5L);
        airport.setOperator(currentOperator);
        AirportEmployee airportAdmin = new AirportEmployee();
        airportAdmin.setAirport(airport);
        airportAdmin.setOperator(oldOperator);
        airportAdmin.setAirportRole(AirportEmployee.AirportRole.ADMIN);
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRPORT_EMPLOYEE", "ACTIVE", true));
        when(airportEmployees.findByUserId(17L)).thenReturn(Optional.of(airportAdmin));
        assertThrows(IllegalEndpoint.class, () -> service.getAirportEmployees(null));
        verify(airportEmployees, never()).findEmployeesByAirportId(anyLong(), anyLong(), any());
    }
}

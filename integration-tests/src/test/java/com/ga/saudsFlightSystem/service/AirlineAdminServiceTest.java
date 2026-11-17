package com.ga.saudsFlightSystem.service;

import com.ga.common.contract.identity.CustomerListResponse;
import com.ga.common.contract.identity.CustomerResponse;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.identity.IdentityServiceClient;
import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AirlineAdminServiceTest {
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AirlineEmployeeRepository employees = mock(AirlineEmployeeRepository.class);
    private final FlightRepository flights = mock(FlightRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AirplaneRepository airplanes = mock(AirplaneRepository.class);
    private final AirplaneRequestRepository requests = mock(AirplaneRequestRepository.class);
    private final IdentityServiceClient identity = mock(IdentityServiceClient.class);
    private final AirlineAdminService service = new AirlineAdminService(
            actors, employees, flights, payments, airplanes, requests, identity);
    private final AirlineEmployee admin = new AirlineEmployee();
    private final Airplane airplane = new Airplane();
    private final LocalDate date = LocalDate.of(2026, 11, 16);

    @BeforeEach
    void setup() {
        Airline airline = new Airline();
        airline.setId(6L);
        admin.setAirline(airline);
        admin.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        airplane.setId(8L);
        airplane.setAirline(airline);
        airplane.setStatus(Airplane.Status.GROUNDED);
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.of(admin));
    }

    @Test
    void allReadsRequireAnActiveAirlineAdmin() {
        for (CurrentActor actor : List.of(
                new CurrentActor(17L, "CUSTOMER", "ACTIVE", true),
                new CurrentActor(17L, "AIRLINE_EMPLOYEE", "DEACTIVATED", true),
                new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", false))) {
            when(actors.getCurrentActor()).thenReturn(actor);
            assertForbidden();
        }
        when(actors.getCurrentActor()).thenReturn(new CurrentActor(17L, "AIRLINE_EMPLOYEE", "ACTIVE", true));
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.empty());
        assertForbidden();
        when(employees.findByUserIdAndRole(17L, AirlineEmployee.AirlineRole.ADMIN)).thenReturn(Optional.of(admin));
        admin.setAirline(null);
        assertForbidden();
        verifyNoInteractions(flights, payments, airplanes, requests, identity);
    }

    private void assertForbidden() {
        assertThrows(IllegalEndpoint.class, () -> service.getDashboard(date, date));
        assertThrows(IllegalEndpoint.class, () -> service.getCustomers("ali", 0, 20));
        assertThrows(IllegalEndpoint.class, () -> service.getAirplaneRequests(8L, 0, 20));
    }

    @Test
    void dashboardUsesInclusiveDatesAndRoundsOccupancyWithoutInventingRefunds() {
        FlightRepository.AirlineDashboardDay day = mock(FlightRepository.AirlineDashboardDay.class);
        when(day.getDate()).thenReturn(date);
        when(day.getOccupiedSeats()).thenReturn(1L);
        when(day.getSeatCapacity()).thenReturn(3L);
        when(flights.findAirlineDashboardDays(6L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(day));
        PaymentRepository.AirlineRevenue payment = mock(PaymentRepository.AirlineRevenue.class);
        when(payment.getDate()).thenReturn(date);
        when(payment.getCurrency()).thenReturn("BHD");
        when(payment.getPaidTotal()).thenReturn(120001L);
        when(payments.findAirlineRevenue(6L, date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(payment));

        var response = service.getDashboard(date, date);
        assertEquals(new BigDecimal("33.33"), response.getDays().get(0).getOccupancyPercentage());
        assertEquals(120001L, response.getRevenue().get(0).getPaidTotal());
        assertNull(response.getRevenue().get(0).getRefundedTotal());
        assertFalse(response.isRefundsSupported());
        assertEquals("Asia/Bahrain", response.getTimeZone());
        assertEquals("SCHEDULED_DEPARTURE", response.getRevenueDateBasis());

        when(day.getSeatCapacity()).thenReturn(0L);
        assertNull(service.getDashboard(date, date).getDays().get(0).getOccupancyPercentage());
    }

    @Test
    void invalidDatesAndPaginationFailBeforeQueries() {
        assertThrows(InvalidInformationException.class, () -> service.getDashboard(null, date));
        assertThrows(InvalidInformationException.class, () -> service.getDashboard(date, date.minusDays(1)));
        assertThrows(InvalidInformationException.class, () -> service.getDashboard(date, date.plusDays(366)));
        assertThrows(InvalidInformationException.class, () -> service.getDashboard(LocalDate.MIN, date));
        assertThrows(InvalidInformationException.class, () -> service.getDashboard(date, LocalDate.MAX));
        assertThrows(InvalidInformationException.class, () -> service.getCustomers(" ", 0, 20));
        assertThrows(InvalidInformationException.class, () -> service.getCustomers("a".repeat(101), 0, 20));
        assertThrows(InvalidInformationException.class, () -> service.getCustomers("ali", -1, 20));
        assertThrows(InvalidInformationException.class, () -> service.getCustomers("ali", 0, 101));
        assertThrows(InvalidInformationException.class, () -> service.getAirplaneRequests(8L, Integer.MAX_VALUE, 20));
        assertThrows(InvalidInformationException.class, () -> service.getAirplaneRequests(0L, 0, 20));
        verifyNoInteractions(flights, payments, airplanes, requests, identity);
    }

    @Test
    void maximumDateRangeAndEmptyResultsAreSupported() {
        var response = service.getDashboard(date, date.plusDays(365));
        assertTrue(response.getDays().isEmpty());
        assertTrue(response.getRevenue().isEmpty());
        verify(flights).findAirlineDashboardDays(6L, date.atStartOfDay(), date.plusDays(366).atStartOfDay());
    }

    @Test
    void customerSearchPreservesIdentityPaginationAndMinimalFields() {
        var customer = new CustomerResponse(40L, "Ali Ahmed", "ali@example.test");
        var expected = new CustomerListResponse(List.of(customer), 1, 20, 21, 2);
        when(identity.customers("Ali", 1, 20)).thenReturn(expected);
        assertSame(expected, service.getCustomers(" Ali ", 1, 20));
    }

    @Test
    void foreignAirplaneDoesNotExposeRequests() {
        assertThrows(InformationNotFoundException.class, () -> service.getAirplaneRequests(8L, 0, 20));
        verify(airplanes).findByIdAndAirline_Id(8L, 6L);
        verifyNoInteractions(requests);
    }

    @Test
    void cooldownUsesLatestRequestEvenWhenRequestedPageIsEmpty() {
        when(airplanes.findByIdAndAirline_Id(8L, 6L)).thenReturn(Optional.of(airplane));
        when(requests.findByAirplane_IdOrderByRequestedAtDescIdDesc(8L, PageRequest.of(2, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 1));
        AirplaneRequest latest = new AirplaneRequest();
        latest.setRequestedAt(LocalDateTime.now(ZoneId.of("Asia/Bahrain")).minusDays(1));
        latest.setStatus(AirplaneRequest.ApprovalStatus.DENIED);
        when(requests.findFirstByAirplane_IdOrderByRequestedAtDescIdDesc(8L)).thenReturn(Optional.of(latest));

        var response = service.getAirplaneRequests(8L, 2, 20);
        assertTrue(response.getContent().isEmpty());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1L, response.getTotalPages());
        assertFalse(response.isCanRequestActivation());
        assertEquals(latest.getRequestedAt().plusDays(7), response.getNextEligibleRequestAt());

        latest.setRequestedAt(latest.getRequestedAt().minusDays(7));
        assertTrue(service.getAirplaneRequests(8L, 2, 20).isCanRequestActivation());
        when(requests.existsByAirplane_IdAndStatus(8L, AirplaneRequest.ApprovalStatus.PENDING)).thenReturn(true);
        response = service.getAirplaneRequests(8L, 2, 20);
        assertFalse(response.isCanRequestActivation());
        assertNull(response.getNextEligibleRequestAt());
    }

    @Test
    void groundedAirplaneWithNoRequestsIsEligibleButActiveAirplaneIsNot() {
        when(airplanes.findByIdAndAirline_Id(8L, 6L)).thenReturn(Optional.of(airplane));
        when(requests.findByAirplane_IdOrderByRequestedAtDescIdDesc(8L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));
        var response = service.getAirplaneRequests(8L, 0, 20);
        assertTrue(response.isCanRequestActivation());
        assertNull(response.getNextEligibleRequestAt());
        airplane.setStatus(Airplane.Status.ACTIVE);
        assertFalse(service.getAirplaneRequests(8L, 0, 20).isCanRequestActivation());
    }
}

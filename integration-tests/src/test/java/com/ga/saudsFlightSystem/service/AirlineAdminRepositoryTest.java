package com.ga.saudsFlightSystem.service;

import com.ga.identity.model.User;
import com.ga.identity.model.UserProfile;
import com.ga.identity.repository.UserRepository;
import com.ga.identity.service.AccountService;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Only runs against an explicitly supplied disposable database named airline_admin_test.
@EnabledIfEnvironmentVariable(named = "AIRLINE_ADMIN_TEST_DATABASE_URL", matches = "jdbc:postgresql://.+/airline_admin_test")
@ActiveProfiles("airline-admin-repository-test")
@SpringBootTest(classes = AirlineAdminRepositoryTest.RepositoryConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.location=optional:classpath:/airline-admin-repository-test.properties",
        "spring.datasource.url=${AIRLINE_ADMIN_TEST_DATABASE_URL}",
        "spring.datasource.username=${AIRLINE_ADMIN_TEST_DATABASE_USERNAME:postgres}",
        "spring.datasource.password=${AIRLINE_ADMIN_TEST_DATABASE_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Transactional
class AirlineAdminRepositoryTest {
    @Configuration
    @Profile("airline-admin-repository-test")
    @EnableAutoConfiguration
    @EntityScan({"com.ga.saudsFlightSystem.model", "com.ga.identity.model"})
    @EnableJpaRepositories(basePackages = {"com.ga.saudsFlightSystem.repository", "com.ga.identity.repository"},
            nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
    static class RepositoryConfiguration {}

    @Autowired EntityManager em;
    @Autowired FlightRepository flights;
    @Autowired PaymentRepository payments;
    @Autowired UserRepository users;
    @Autowired AirplaneRepository airplanes;
    @Autowired AirplaneRequestRepository requests;

    @Test
    void airportFlightsIncludeAllAirlinesButOnlyOwnedDepartures() {
        AirportOperator operator = new AirportOperator();
        operator.setName("Airport test operator");
        operator.setContactEmail("airport-operator@example.test");
        em.persist(operator);

        Airport airport = new Airport();
        airport.setOperator(operator);
        em.persist(airport);
        Airport otherAirport = new Airport();
        otherAirport.setOperator(operator);
        em.persist(otherAirport);

        LocalDate date = LocalDate.of(2026, 11, 17);
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Flight first = flight(airline(), start, Flight.FlightStatus.ACTIVE, 10);
        first.setOriginAirport(airport);
        Flight second = flight(airline(), start, Flight.FlightStatus.ACTIVE, 10);
        second.setOriginAirport(airport);

        Flight arrival = flight(airline(), start, Flight.FlightStatus.ACTIVE, 10);
        arrival.setOriginAirport(otherAirport);
        arrival.setDestinationAirport(airport);

        Flight outsideRange = flight(airline(), end, Flight.FlightStatus.ACTIVE, 10);
        outsideRange.setOriginAirport(airport);
        Flight cancelled = flight(airline(), start, Flight.FlightStatus.CANCELLED, 10);
        cancelled.setOriginAirport(airport);
        em.flush();
        em.clear();

        assertEquals(2L, flights.countAirportFlights(
                airport.getId(), operator.getId(), "ACTIVE", start, end));
        assertEquals(List.of(second.getId()), flights.findAirportFlightIds(
                airport.getId(), operator.getId(), "ACTIVE", start, end, 1, 0L));
        assertEquals(List.of(first.getId()), flights.findAirportFlightIds(
                airport.getId(), operator.getId(), "ACTIVE", start, end, 1, 1L));
        assertEquals(3L, flights.countAirportFlights(
                airport.getId(), operator.getId(), null, start, end));

        var details = flights.findAirportFlightsWithDetails(
                airport.getId(), operator.getId(), List.of(first.getId(), arrival.getId()));
        assertEquals(1, details.size());
        assertEquals(first.getId(), details.get(0).getId());
        assertEquals(first.getAirline().getId(), details.get(0).getAirlineId());
        assertEquals(airport.getId(), details.get(0).getOriginAirportId());
        assertNull(details.get(0).getAirplaneId());
        assertNull(details.get(0).getDestinationAirportId());

        assertEquals(0L, flights.countAirportFlights(airport.getId(), -1L, null, null, null));
        assertTrue(flights.findAirportFlightIds(
                airport.getId(), -1L, null, null, null, 20, 0L).isEmpty());
        assertTrue(flights.findAirportFlightsWithDetails(
                airport.getId(), -1L, List.of(first.getId())).isEmpty());
    }

    @Test
    void dailyCountsAndPaymentsKeepAirlinesDatesAndCurrenciesSeparate() {
        LocalDate date = LocalDate.of(2026, 11, 16);
        Airline airline = airline();
        Flight flight = flight(airline, date.atStartOfDay(), Flight.FlightStatus.ACTIVE, 7);
        payment(booking(flight, Booking.BookingStatus.BOOKED), Currency.BHD, 1001, Payment.PaymentStatus.PAID);
        payment(booking(flight, Booking.BookingStatus.FINISHED), Currency.BHD, 2002, Payment.PaymentStatus.PAID);
        payment(booking(flight, Booking.BookingStatus.BOOKED), Currency.USD, 9000, Payment.PaymentStatus.PENDING);
        payment(booking(flight, Booking.BookingStatus.CANCELLED), Currency.USD, 55, Payment.PaymentStatus.PAID);
        flight(airline, date.atTime(23, 59, 59), Flight.FlightStatus.PENDING_CREW, 20);
        Flight cancelled = flight(airline, date.atTime(12, 0), Flight.FlightStatus.CANCELLED, 49);
        payment(booking(cancelled, Booking.BookingStatus.BOOKED), Currency.BHD, 333, Payment.PaymentStatus.PAID);
        Flight foreign = flight(airline(), date.atStartOfDay(), Flight.FlightStatus.ACTIVE, 9);
        payment(booking(foreign, Booking.BookingStatus.BOOKED), Currency.BHD, 9999, Payment.PaymentStatus.PAID);
        Flight nextDay = flight(airline, date.plusDays(1).atStartOfDay(), Flight.FlightStatus.ACTIVE, 9);
        payment(booking(nextDay, Booking.BookingStatus.BOOKED), Currency.BHD, 9999, Payment.PaymentStatus.PAID);
        flight(airline, date.atStartOfDay().minusSeconds(1), Flight.FlightStatus.ACTIVE, 10);
        em.flush();
        em.clear();

        var days = flights.findAirlineDashboardDays(airline.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        assertEquals(1, days.size());
        var day = days.get(0);
        assertEquals(date, day.getDate());
        assertEquals(3, day.getFlightCount());
        assertEquals(5, day.getBookingCount());
        assertEquals(3, day.getBookedBookings());
        assertEquals(1, day.getCancelledBookings());
        assertEquals(1, day.getFinishedBookings());
        assertEquals(3, day.getOccupiedSeats());
        assertEquals(30, day.getSeatCapacity());

        var revenue = payments.findAirlineRevenue(airline.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        assertEquals(2, revenue.size());
        assertEquals(date, revenue.get(0).getDate());
        assertEquals("BHD", revenue.get(0).getCurrency());
        assertEquals(3336, revenue.get(0).getPaidTotal());
        assertEquals("USD", revenue.get(1).getCurrency());
        assertEquals(55, revenue.get(1).getPaidTotal());
        assertTrue(flights.findAirlineDashboardDays(airline.getId(), date.minusDays(3).atStartOfDay(),
                date.minusDays(2).atStartOfDay()).isEmpty());
    }

    @Test
    void customerSearchFiltersAccountStateAndPaginatesWithoutReturningAccountSecrets() {
        User first = user("lookup-first@example.test", User.Role.CUSTOMER, User.Status.ACTIVE, true);
        first.setPersonalProfile(new UserProfile("Ali", "Ahmed", UUID.randomUUID().toString(), null, null));
        em.persist(first.getPersonalProfile());
        User second = user("lookup-second@example.test", User.Role.CUSTOMER, User.Status.ACTIVE, true);
        user("lookup-staff@example.test", User.Role.AIRLINE_EMPLOYEE, User.Status.ACTIVE, true);
        user("lookup-inactive@example.test", User.Role.CUSTOMER, User.Status.ACTIVE, false);
        user("lookup-deactivated@example.test", User.Role.CUSTOMER, User.Status.DEACTIVATED, true);
        user("lookup-setup@example.test", User.Role.CUSTOMER, User.Status.SETUP_REQUIRED, true);
        em.flush();
        em.clear();

        AccountService accounts = new AccountService(users, null);
        var page = accounts.findCustomers(" LOOKUP- ", 0, 1);
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(first.getId(), page.content().get(0).userId());
        assertEquals("Ali Ahmed", page.content().get(0).displayName());
        assertEquals("lookup-first@example.test", page.content().get(0).emailAddress());
        assertEquals(second.getId(), accounts.findCustomers("lookup-", 1, 1).content().get(0).userId());
        assertTrue(accounts.findCustomers("lookup-", 2, 1).content().isEmpty());
        assertEquals(first.getId(), accounts.findCustomers("ALI AHMED", 0, 20).content().get(0).userId());
        assertEquals(first.getId(), accounts.findCustomers(first.getId().toString(), 0, 20).content().get(0).userId());
    }

    @Test
    void searchTreatsWildcardCharactersLiterallyAndHandlesMissingProfiles() {
        User customer = user("percent%_name@example.test", User.Role.CUSTOMER, User.Status.ACTIVE, true);
        user("ordinary@example.test", User.Role.CUSTOMER, User.Status.ACTIVE, true);
        em.flush();
        em.clear();
        var result = users.findCustomers("%_", User.Role.CUSTOMER, User.Status.ACTIVE, PageRequest.of(0, 20));
        assertEquals(1, result.getTotalElements());
        assertEquals(customer.getId(), result.getContent().get(0).userId());
        assertEquals("", result.getContent().get(0).displayName());
    }

    @Test
    void activationHistoryUsesStableOrderingAndChecksAllPendingRequests() {
        Airline airline = airline();
        Airplane airplane = airplane(airline);
        Airplane foreign = airplane(airline());
        AirplaneRequest older = request(airplane, AirplaneRequest.ApprovalStatus.PENDING);
        AirplaneRequest newer = request(airplane, AirplaneRequest.ApprovalStatus.DENIED);
        request(foreign, AirplaneRequest.ApprovalStatus.PENDING);
        em.flush();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 11, 1, 12, 0);
        em.createQuery("UPDATE AirplaneRequest r SET r.requestedAt = :time WHERE r.airplane.id = :airplaneId")
                .setParameter("time", requestedAt).setParameter("airplaneId", airplane.getId()).executeUpdate();
        em.clear();

        var page = requests.findByAirplane_IdOrderByRequestedAtDescIdDesc(airplane.getId(), PageRequest.of(0, 1));
        assertEquals(2, page.getTotalElements());
        assertEquals(newer.getId(), page.getContent().get(0).getId());
        assertEquals(older.getId(), requests.findByAirplane_IdOrderByRequestedAtDescIdDesc(
                airplane.getId(), PageRequest.of(1, 1)).getContent().get(0).getId());
        assertEquals(newer.getId(), requests.findFirstByAirplane_IdOrderByRequestedAtDescIdDesc(airplane.getId()).orElseThrow().getId());
        assertTrue(requests.existsByAirplane_IdAndStatus(airplane.getId(), AirplaneRequest.ApprovalStatus.PENDING));
        assertTrue(airplanes.findByIdAndAirline_Id(airplane.getId(), airline.getId()).isPresent());
        assertTrue(airplanes.findByIdAndAirline_Id(foreign.getId(), airline.getId()).isEmpty());
    }

    private Airline airline() {
        Airline airline = new Airline();
        airline.setName(UUID.randomUUID().toString());
        airline.setAirlineCode(UUID.randomUUID().toString());
        em.persist(airline);
        return airline;
    }

    private Flight flight(Airline airline, LocalDateTime departure, Flight.FlightStatus status, int remainingSeats) {
        Flight flight = new Flight();
        flight.setAirline(airline);
        flight.setScheduledDeparture(departure);
        flight.setStatus(status);
        flight.setStandardSeatsCount(remainingSeats);
        em.persist(flight);
        return flight;
    }

    private Booking booking(Flight flight, Booking.BookingStatus status) {
        Booking booking = new Booking();
        booking.setFlight(flight);
        booking.setStatus(status);
        em.persist(booking);
        return booking;
    }

    private void payment(Booking booking, Currency currency, long amount, Payment.PaymentStatus status) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setCurrency(currency);
        payment.setAmount(amount);
        payment.setStatus(status);
        em.persist(payment);
    }

    private User user(String email, User.Role role, User.Status status, boolean active) {
        User user = new User();
        user.setEmailAddress(email);
        user.setPassword("unused-test-password");
        user.setRole(role);
        user.setStatus(status);
        user.setActive(active);
        em.persist(user);
        return user;
    }

    private Airplane airplane(Airline airline) {
        Airplane airplane = new Airplane();
        airplane.setAirline(airline);
        airplane.setRegistrationNumber(UUID.randomUUID().toString());
        em.persist(airplane);
        return airplane;
    }

    private AirplaneRequest request(Airplane airplane, AirplaneRequest.ApprovalStatus status) {
        AirplaneRequest request = new AirplaneRequest();
        request.setAirplane(airplane);
        request.setStatus(status);
        em.persist(request);
        return request;
    }
}

package com.ga.saudsFlightSystem.config;

import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Optional demo data for a local PostgreSQL database. Existing records are never reset. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DatabaseSeeder implements CommandLineRunner {
    private final EntityManager em;
    private final PasswordEncoder passwords;
    private static final Logger logger = Logger.getLogger(DatabaseSeeder.class.getName());

    public DatabaseSeeder(EntityManager em, PasswordEncoder passwords) {
        this.em = em;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Serialize seed runs from multiple application instances.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(900001)").getSingleResult();
        Airline test = airline("TST", "Test Seed Airline", "Bahrain");
        Airline demo = airline("DMA", "Demo Airways", "United Arab Emirates");
        User customer = user("flighttest.customer@mailsac.com", "Test", "Customer", "990000001", User.Role.CUSTOMER, null);
        user("flighttest.faa@mailsac.com", "Test", "Inspector", "990000002", User.Role.FAA_ADMIN, null);
        User admin = user("flighttest.airline@mailsac.com", "Test", "Administrator", "990000003", User.Role.AIRLINE_EMPLOYEE, test);
        User sara = user("sara.customer@example.com", "Sara", "Ahmed", "990000004", User.Role.CUSTOMER, null);
        User omar = user("omar.customer@example.com", "Omar", "Ali", "990000005", User.Role.CUSTOMER, null);
        user("layla.customer@example.com", "Layla", "Hassan", "990000006", User.Role.CUSTOMER, null);
        user("admin.demo@example.com", "Noor", "Khalid", "990000007", User.Role.AIRLINE_EMPLOYEE, demo);

        Airport bah = airport("BAH", "Bahrain International Airport", "Muharraq", "Bahrain", "Asia/Bahrain");
        Airport dxb = airport("DXB", "Dubai International Airport", "Dubai", "United Arab Emirates", "Asia/Dubai");
        Airport doh = airport("DOH", "Hamad International Airport", "Doha", "Qatar", "Asia/Qatar");

        // Insert the reserved airplane before generating other airplane IDs.
        freeAirplane(test);
        advanceSequence("airplanes");
        Airplane bookingPlane = airplane("TEST-BOOKING-PLANE", test, 2, 2, Airplane.Status.ACTIVE);
        Airplane grounded = airplane("TEST-GROUNDED-PLANE", test, 100, 10, Airplane.Status.GROUNDED);
        activationRequest(grounded, admin);
        advanceSequence("airplane_requests");

        LocalDateTime departure = LocalDateTime.now().plusDays(3).withNano(0);
        Flight fixture = flight("TEST101", bookingPlane, bah, dxb, departure);
        fixedBooking(customer, fixture);
        advanceSequence("bookings");

        Airplane regional = airplane("DEMO-TST-01", test, 120, 12, Airplane.Status.ACTIVE);
        Airplane international = airplane("DEMO-DMA-01", demo, 150, 16, Airplane.Status.ACTIVE);
        Flight outbound = flight("TST201", regional, bah, doh, departure.plusDays(1));
        flight("TST202", regional, doh, bah, departure.plusDays(2));
        Flight dubai = flight("DMA301", international, dxb, bah, departure.plusDays(1));
        flight("DMA302", international, bah, dxb, departure.plusDays(2));
        booking("DEMO-BOOKING-001", sara, outbound, "standard-1", Booking.BookingStatus.BOOKED);
        booking("DEMO-BOOKING-002", omar, dubai, "firstClass-1", Booking.BookingStatus.BOOKED);
        booking("DEMO-BOOKING-003", sara, dubai, "standard-2", Booking.BookingStatus.CANCELLED);
        em.flush();
        logger.info("Database seed completed; existing records were preserved.");
    }

    private <T> T findOrCreate(Class<T> type, String field, String key, Supplier<T> factory) {
        return em.createQuery("select e from " + type.getSimpleName() + " e where e." + field + " = :key", type)
                .setParameter("key", key).getResultStream().findFirst().orElseGet(() -> {
                    T entity = factory.get();
                    em.persist(entity);
                    return entity;
                });
    }

    private Airline airline(String code, String name, String country) {
        return findOrCreate(Airline.class, "airlineCode", code, () -> {
            Airline airline = new Airline();
            airline.setAirlineCode(code);
            airline.setName(name);
            airline.setHeadquartersCountry(country);
            return airline;
        });
    }

    private User user(String email, String first, String last, String cpr, User.Role role, Airline airline) {
        return findOrCreate(User.class, "emailAddress", email, () -> {
            User user = new User();
            user.setEmailAddress(email);
            user.setPassword(passwords.encode("TestPassword1"));
            user.setSecurityQuestion("What is your demo city?");
            user.setSecurityQuestionAnswer(passwords.encode("manama"));
            user.setRole(role);
            user.setStatus(User.Status.ACTIVE);
            user.setActive(true);
            user.setFailedLoginAttempts(0);
            Person profile;
            if (role == User.Role.CUSTOMER) {
                Customer value = new Customer();
                user.setCustomer(value);
                profile = value;
            } else if (role == User.Role.FAA_ADMIN) {
                FAAAdmin value = new FAAAdmin();
                user.setFaaAdmin(value);
                profile = value;
            } else {
                AirlineEmployee value = new AirlineEmployee();
                value.setAirline(airline);
                value.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
                value.setHireDate(LocalDate.of(2025, 1, 1));
                value.setSalary(1500L);
                user.setAirlineEmployee(value);
                profile = value;
            }
            profile.setFName(first);
            profile.setLName(last);
            profile.setCpr(cpr);
            profile.setPhoneNumberOpeningCode("+973");
            profile.setPhoneNumber("360010" + cpr.substring(7));
            return user;
        });
    }

    private Airport airport(String code, String name, String city, String country, String zone) {
        return findOrCreate(Airport.class, "iataCode", code, () -> {
            Airport airport = new Airport();
            airport.setIataCode(code);
            airport.setName(name);
            airport.setCity(city);
            airport.setCountry(country);
            airport.setTimeZone(zone);
            return airport;
        });
    }

    private Airplane airplane(String registration, Airline airline, int standard, int first, Airplane.Status status) {
        return findOrCreate(Airplane.class, "registrationNumber", registration, () -> {
            Airplane plane = new Airplane();
            plane.setRegistrationNumber(registration);
            plane.setModel("Demo passenger aircraft");
            plane.setAirline(airline);
            plane.setStandardSeatsCapacity(standard);
            plane.setFirstClassSeatsCapacity(first);
            plane.setMaxMileage(6000L);
            plane.setStatus(status);
            return plane;
        });
    }

    private Flight flight(String number, Airplane plane, Airport origin, Airport destination, LocalDateTime departure) {
        return findOrCreate(Flight.class, "flightNumber", number, () -> {
            Flight flight = new Flight();
            flight.setFlightNumber(number);
            flight.setAirplane(plane);
            flight.setAirline(plane.getAirline());
            flight.setOriginAirport(origin);
            flight.setDestinationAirport(destination);
            flight.setScheduledDeparture(departure);
            flight.setScheduledArrival(departure.plusHours(2));
            flight.setStatus(Flight.FlightStatus.ACTIVE);
            flight.setStandardSeatsCount(plane.getStandardSeatsCapacity());
            flight.setFirstClassSeatsCount(plane.getFirstClassSeatsCapacity());
            return flight;
        });
    }

    private void booking(String reference, User owner, Flight flight, String seat, Booking.BookingStatus status) {
        findOrCreate(Booking.class, "bookingRef", reference, () -> {
            Booking booking = new Booking();
            booking.setBookingRef(reference);
            booking.setUser(owner);
            booking.setFlight(flight);
            booking.setSeatNumber(seat);
            booking.setStatus(status);
            if (status == Booking.BookingStatus.BOOKED) reserveSeat(flight, seat);
            return booking;
        });
    }

    private void reserveSeat(Flight flight, String seat) {
        long occupied = em.createQuery("select count(b) from Booking b where b.flight = :flight and b.seatNumber = :seat and b.status = :status", Long.class)
                .setParameter("flight", flight).setParameter("seat", seat)
                .setParameter("status", Booking.BookingStatus.BOOKED).getSingleResult();
        boolean standard = seat.startsWith("standard-");
        int available = standard ? flight.getStandardSeatsCount() : flight.getFirstClassSeatsCount();
        if (occupied != 0 || available < 1) throw new IllegalStateException("Seed seat is unavailable: " + flight.getFlightNumber() + " / " + seat);
        if (standard) flight.setStandardSeatsCount(available - 1);
        else flight.setFirstClassSeatsCount(available - 1);
    }

    private void freeAirplane(Airline airline) {
        Airplane existing = em.find(Airplane.class, 900003L);
        if (existing != null) {
            require(Objects.equals(existing.getRegistrationNumber(), "TEST-FREE-PLANE") && Objects.equals(existing.getAirline().getId(), airline.getId()), "airplane 900003");
            return;
        }
        em.createNativeQuery("""
                INSERT INTO airplanes (id, registration_number, model, standard_seats_capacity,
                first_class_seats_capacity, max_mileage, added_at, status, airline_id)
                VALUES (900003, 'TEST-FREE-PLANE', 'Test passenger aircraft', 100, 10, 6000, CURRENT_TIMESTAMP, 'ACTIVE', :airline)
                """).setParameter("airline", airline.getId()).executeUpdate();
    }

    private void activationRequest(Airplane plane, User admin) {
        AirplaneRequest existing = em.find(AirplaneRequest.class, 900002L);
        if (existing != null) {
            require(Objects.equals(existing.getAirplane().getId(), plane.getId()) && existing.getRequestedBy() != null
                    && Objects.equals(existing.getRequestedBy().getId(), admin.getId()), "activation request 900002");
            return;
        }
        em.createNativeQuery("""
                INSERT INTO airplane_requests (id, airplane_id, status, requested_at, requested_by_id)
                VALUES (900002, :plane, 'PENDING', CURRENT_TIMESTAMP, :admin)
                """).setParameter("plane", plane.getId()).setParameter("admin", admin.getId()).executeUpdate();
    }

    private void fixedBooking(User customer, Flight flight) {
        Booking existing = em.find(Booking.class, 900001L);
        if (existing != null) {
            require(Objects.equals(existing.getBookingRef(), "TEST-BOOKING-900001") && existing.getUser() != null
                    && Objects.equals(existing.getUser().getId(), customer.getId()) && existing.getFlight() != null
                    && Objects.equals(existing.getFlight().getId(), flight.getId()), "booking 900001");
            return;
        }
        reserveSeat(flight, "standard-1");
        em.flush();
        em.createNativeQuery("""
                INSERT INTO bookings (id, booking_ref, user_id, flight_id, seat_number, status, booked_at, updated_at)
                VALUES (900001, 'TEST-BOOKING-900001', :customer, :flight, 'standard-1', 'BOOKED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter("customer", customer.getId()).setParameter("flight", flight.getId()).executeUpdate();
    }

    private void require(boolean matches, String record) {
        if (!matches) throw new IllegalStateException("Seed ID conflicts with existing " + record + "; no records were overwritten.");
    }

    private void advanceSequence(String table) {
        // Table names are internal constants. Never move a sequence backwards, even after deletions.
        em.flush();
        em.createNativeQuery("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                + "GREATEST((SELECT COALESCE(MAX(id), 1) FROM " + table + "), "
                + "COALESCE(pg_sequence_last_value(CAST(pg_get_serial_sequence('" + table + "', 'id') AS regclass)), 1)), true)")
                .getSingleResult();
    }
}

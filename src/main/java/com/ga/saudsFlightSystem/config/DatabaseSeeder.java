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

/** Optional local sample data. Missing legacy fields are upgraded; workflow progress is never reset. */
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
        Airline test = airline("GF", "Gulf Air", "Bahrain");
        Airline emirates = airline("EK", "Emirates", "United Arab Emirates");
        User customer = user("flighttest.customer@mailsac.com", "Ahmed", "Yousif", "990000001", User.Role.CUSTOMER, null);
        user("flighttest.faa@mailsac.com", "Fatima", "Salman", "990000002", User.Role.FAA_ADMIN, null);
        User admin = user("flighttest.airline@mailsac.com", "Hassan", "Mahmood", "990000003", User.Role.AIRLINE_EMPLOYEE, test);
        User sara = user("sara.customer@mailsac.com", "Sara", "Ahmed", "990000004", User.Role.CUSTOMER, null);
        User omar = user("omar.customer@mailsac.com", "Omar", "Ali", "990000005", User.Role.CUSTOMER, null);
        user("layla.customer@mailsac.com", "Layla", "Hassan", "990000006", User.Role.CUSTOMER, null);
        user("noor.airline@mailsac.com", "Noor", "Khalid", "990000007", User.Role.AIRLINE_EMPLOYEE, emirates);

        Airport bah = airport("BAH", "Bahrain International Airport", "Muharraq", "Bahrain", "Asia/Bahrain");
        Airport dxb = airport("DXB", "Dubai International Airport", "Dubai", "United Arab Emirates", "Asia/Dubai");
        Airport doh = airport("DOH", "Hamad International Airport", "Doha", "Qatar", "Asia/Qatar");

        // Insert the reserved airplane before generating other airplane IDs.
        freeAirplane(test);
        advanceSequence("airplanes");
        Airplane bookingPlane = airplane("A9C-SF01", "Airbus A320-200", test, 138, 12, Airplane.Status.ACTIVE);
        Airplane grounded = airplane("A9C-SF02", "Airbus A321neo", test, 154, 16, Airplane.Status.GROUNDED);
        activationRequest(grounded, admin);
        advanceSequence("airplane_requests");

        LocalDateTime departure = LocalDateTime.now().plusDays(3).withNano(0);
        Flight fixture = flight("GF501", bookingPlane, bah, dxb, departure);
        fixedBooking(customer, fixture);
        advanceSequence("bookings");

        Airplane regional = airplane("A9C-SF03", "Airbus A320-200", test, 138, 12, Airplane.Status.ACTIVE);
        Airplane international = airplane("A6-SF01", "Boeing 777-300ER", emirates, 310, 8, Airplane.Status.ACTIVE);
        Flight outbound = flight("GF524", regional, bah, doh, departure.plusDays(1));
        flight("GF525", regional, doh, bah, departure.plusDays(2));
        Flight dubai = flight("EK837", international, dxb, bah, departure.plusDays(1));
        flight("EK838", international, bah, dxb, departure.plusDays(2));
        booking("SFA7K2", sara, outbound, "standard-1", Booking.BookingStatus.BOOKED);
        booking("SFB8L3", omar, dubai, "firstClass-1", Booking.BookingStatus.BOOKED);
        booking("SFC9M4", sara, dubai, "standard-2", Booking.BookingStatus.CANCELLED);
        projectThree(test, bah, dxb, bookingPlane, omar, departure.plusDays(3));
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
            user.setSecurityQuestion("In which city were you born?");
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

    private Airplane airplane(String registration, String model, Airline airline, int standard, int first, Airplane.Status status) {
        return findOrCreate(Airplane.class, "registrationNumber", registration, () -> {
            Airplane plane = new Airplane();
            plane.setRegistrationNumber(registration);
            plane.setModel(model);
            plane.setAirline(airline);
            plane.setStandardSeatsCapacity(standard);
            plane.setFirstClassSeatsCapacity(first);
            plane.setMaxMileage(6000L);
            plane.setStatus(status);
            return plane;
        });
    }

    private Flight flight(String number, Airplane plane, Airport origin, Airport destination, LocalDateTime departure) {
        return flight(number, plane, origin, destination, departure, true);
    }

    private Flight flight(String number, Airplane plane, Airport origin, Airport destination,
                          LocalDateTime departure, boolean ready) {
        Flight result = findOrCreate(Flight.class, "flightNumber", number, () -> {
            Flight flight = new Flight();
            flight.setFlightNumber(number);
            flight.setAirplane(plane);
            flight.setAirline(plane.getAirline());
            flight.setOriginAirport(origin);
            flight.setDestinationAirport(destination);
            flight.setScheduledDeparture(departure);
            flight.setScheduledArrival(departure.plusHours(2));
            flight.setStatus(ready ? Flight.FlightStatus.ACTIVE : Flight.FlightStatus.PENDING_CREW);
            flight.setStandardSeatsCount(plane.getStandardSeatsCapacity());
            flight.setFirstClassSeatsCount(plane.getFirstClassSeatsCapacity());
            return flight;
        });
        // Null prices identify old fixtures (and newly inserted flights). Do not restore
        // crew removed during a demo on subsequent runs or change existing schedules.
        if (result.getStandardPrice() == null && result.getFirstClassPrice() == null) {
            if (ready && result.getStatus() == Flight.FlightStatus.ACTIVE
                    && em.createQuery("select count(c) from CrewAssignment c where c.flight = :flight", Long.class)
                    .setParameter("flight", result).getSingleResult() == 0) {
                seedCrew(result);
            }
        }
        if (result.getStandardPrice() == null) result.setStandardPrice(45000L);
        if (result.getFirstClassPrice() == null) result.setFirstClassPrice(120000L);
        if (result.getCurrency() == null) result.setCurrency(Currency.BHD);
        return result;
    }

    private User staff(String email, String cpr, User.Role role, Supplier<? extends Person> factory) {
        return findOrCreate(User.class, "emailAddress", email, () -> {
            User user = new User();
            user.setEmailAddress(email);
            user.setPassword(passwords.encode("TestPassword1"));
            user.setSecurityQuestion("In which city were you born?");
            user.setSecurityQuestionAnswer(passwords.encode("manama"));
            user.setRole(role);
            user.setStatus(User.Status.ACTIVE);
            user.setActive(true);
            Person profile = factory.get();
            profile.setFName("Sample");
            profile.setLName(email.substring(0, email.indexOf('@')));
            profile.setCpr(cpr);
            profile.setPhoneNumberOpeningCode("+973");
            profile.setPhoneNumber("3600" + cpr.substring(5));
            if (profile instanceof AirlineEmployee employee) user.setAirlineEmployee(employee);
            else if (profile instanceof AirportEmployee employee) user.setAirportEmployee(employee);
            else if (profile instanceof MaintenanceEmployee employee) user.setMaintenanceEmployee(employee);
            else throw new IllegalArgumentException("Unsupported seed staff profile");
            return user;
        });
    }

    private void seedCrew(Flight flight) {
        Duty[] duties = {Duty.PILOT, Duty.SECONDARY_PILOT, Duty.FLIGHT_ATTENDANT};
        String code = flight.getAirline().getAirlineCode().toLowerCase();
        for (int i = 0; i < duties.length; i++) {
            Duty duty = duties[i];
            User user = staff(code + ".crew" + (i + 1) + "@mailsac.com",
                    "9900000" + ((code.equals("gf") ? 20 : 30) + i), User.Role.AIRLINE_EMPLOYEE, () -> {
                        AirlineEmployee employee = new AirlineEmployee();
                        employee.setAirline(flight.getAirline());
                        employee.setAirlineRole(duty == Duty.FLIGHT_ATTENDANT
                                ? AirlineEmployee.AirlineRole.FLIGHT_ATTENDANT : AirlineEmployee.AirlineRole.PILOT);
                        employee.setHireDate(LocalDate.of(2025, 1, 1));
                        employee.setSalary(1500L);
                        return employee;
                    });
            AirlineEmployee employee = user.getAirlineEmployee();
            require(employee != null && user.getRole() == User.Role.AIRLINE_EMPLOYEE
                    && employee.getAirline() != null
                    && Objects.equals(employee.getAirline().getId(), flight.getAirline().getId())
                    && employee.getAirlineRole() == (duty == Duty.FLIGHT_ATTENDANT
                    ? AirlineEmployee.AirlineRole.FLIGHT_ATTENDANT : AirlineEmployee.AirlineRole.PILOT),
                    "crew profile " + user.getEmailAddress());
            // Existing crew may have been reassigned by a user; never introduce overlaps.
            long conflicts = em.createQuery("select count(c) from CrewAssignment c where c.airlineEmployee = :employee "
                            + "and c.flight.status <> :cancelled and c.flight.scheduledDeparture < :arrival "
                            + "and c.flight.scheduledArrival > :departure", Long.class)
                    .setParameter("employee", user.getAirlineEmployee())
                    .setParameter("cancelled", Flight.FlightStatus.CANCELLED)
                    .setParameter("arrival", flight.getScheduledArrival())
                    .setParameter("departure", flight.getScheduledDeparture()).getSingleResult();
            require(conflicts == 0, "crew schedule for " + flight.getFlightNumber());
            CrewAssignment assignment = new CrewAssignment();
            assignment.setFlight(flight);
            assignment.setAirlineEmployee(user.getAirlineEmployee());
            assignment.setDuty(duty);
            em.persist(assignment);
        }
    }

    private void projectThree(Airline airline, Airport airport, Airport destination, Airplane plane,
                              User customer, LocalDateTime departure) {
        AirportOperator operator = airport.getOperator();
        if (operator == null) {
            operator = findOrCreate(AirportOperator.class, "contactEmail", "seed.operator@mailsac.com", () -> {
                AirportOperator value = new AirportOperator();
                value.setName("Sample Bahrain Airport Operator");
                value.setContactEmail("seed.operator@mailsac.com");
                return value;
            });
            airport.setOperator(operator);
        }
        AirportOperator assignedOperator = operator;
        for (AirportEmployee.AirportRole role : new AirportEmployee.AirportRole[]{
                AirportEmployee.AirportRole.ADMIN, AirportEmployee.AirportRole.INSPECTOR}) {
            staff("airport." + role.name().toLowerCase() + "@mailsac.com",
                    role == AirportEmployee.AirportRole.ADMIN ? "990000040" : "990000041",
                    User.Role.AIRPORT_EMPLOYEE, () -> {
                        AirportEmployee employee = new AirportEmployee();
                        employee.setAirport(airport);
                        employee.setOperator(assignedOperator);
                        employee.setAirportRole(role);
                        return employee;
                    });
        }
        Flight pending = flight("GF-P3-READY", plane, airport, destination, departure, false);
        // One initial inspection only; preserve later assignments and re-inspection history.
        if (em.createQuery("select count(i) from FlightInspection i where i.flight = :flight", Long.class)
                .setParameter("flight", pending).getSingleResult() == 0) {
            FlightInspection inspection = new FlightInspection();
            inspection.setFlight(pending);
            inspection.setAirport(airport);
            em.persist(inspection);
        }
        MaintenanceProvider provider = findOrCreate(MaintenanceProvider.class, "contactEmail",
                "seed.maintenance@mailsac.com", () -> {
                    MaintenanceProvider value = new MaintenanceProvider();
                    value.setName("Sample Gulf Maintenance");
                    value.setContactEmail("seed.maintenance@mailsac.com");
                    value.getAirlines().add(airline);
                    return value;
                });
        for (MaintenanceEmployee.MaintenanceRole role : MaintenanceEmployee.MaintenanceRole.values()) {
            staff("maintenance." + role.name().toLowerCase() + "@mailsac.com",
                    role == MaintenanceEmployee.MaintenanceRole.ADMIN ? "990000050" : "990000051",
                    User.Role.MAINTENANCE_EMPLOYEE, () -> {
                        MaintenanceEmployee employee = new MaintenanceEmployee();
                        employee.setProvider(provider);
                        employee.setMaintenanceRole(role);
                        if (role == MaintenanceEmployee.MaintenanceRole.TECHNICIAN)
                            employee.setLicenseNumber("SEED-TECH-001");
                        return employee;
                    });
        }
        findOrCreate(MaintenanceRecord.class, "description", "Project 3 sample routine maintenance", () -> {
            MaintenanceRecord record = new MaintenanceRecord();
            record.setAirplane(plane);
            record.setProvider(provider);
            record.setDescription("Project 3 sample routine maintenance");
            return record;
        });
        Flight paymentFlight = flight("GF-P3-PAY", plane, airport, destination, departure.plusDays(1));
        booking("P3PAY01", customer, paymentFlight, "standard-3", Booking.BookingStatus.BOOKED);
        Booking payable = em.createQuery("select b from Booking b where b.bookingRef = 'P3PAY01'", Booking.class).getSingleResult();
        if (em.createQuery("select count(p) from Payment p where p.booking = :booking", Long.class)
                .setParameter("booking", payable).getSingleResult() == 0) {
            Payment payment = new Payment();
            payment.setBooking(payable);
            payment.setAmount(payable.getFlight().getStandardPrice());
            payment.setCurrency(payable.getFlight().getCurrency());
            payment.setStatus(payable.getStatus() == Booking.BookingStatus.CANCELLED
                    ? Payment.PaymentStatus.EXPIRED : Payment.PaymentStatus.PENDING);
            em.persist(payment);
        }
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
                VALUES (900003, 'TEST-FREE-PLANE', 'Boeing 787-9', 250, 26, 6000, CURRENT_TIMESTAMP, 'ACTIVE', :airline)
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

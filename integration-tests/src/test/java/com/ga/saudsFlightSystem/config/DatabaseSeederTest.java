package com.ga.saudsFlightSystem.config;

import com.ga.identity.model.User;
import static com.ga.saudsFlightSystem.support.IdentityFixtures.*;

import com.ga.saudsFlightSystem.model.*;
import jakarta.persistence.EntityManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Runs against the configured PostgreSQL test database; all row changes roll back. */
@org.springframework.context.annotation.Import(com.ga.saudsFlightSystem.support.IdentityTestConfiguration.class)
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=${seed.test.ddl-auto:validate}",
        "stripe.secret-key=sk_test_unused",
        "stripe.success-url=http://localhost:8080/payments/success",
        "stripe.cancel-url=http://localhost:8080/payments/cancel"
})
@Transactional
public class DatabaseSeederTest {
    @Autowired com.ga.saudsFlightSystem.identity.IdentityClient identity;
    @Autowired EntityManager em;
    @Autowired PasswordEncoder passwords;
    @Autowired com.ga.identity.repository.UserRepository users;

    @Test
    public void seedsRelatedProjectThreeDataAndPreservesProgressOnRerun() {
        new com.ga.identity.config.IdentitySeeder(users, passwords).run();
        DatabaseSeeder seeder = new DatabaseSeeder(em, identity);
        seeder.run();
        em.flush();
        em.clear();

        Flight ready = flight("GF-P3-READY");
        Flight payable = flight("GF-P3-PAY");
        assertNotNull(ready.getOriginAirport().getOperator());
        assertTrue(payable.getStandardPrice() > 0);
        assertTrue(payable.getFirstClassPrice() > 0);
        assertEquals(Currency.BHD, payable.getCurrency());
        assertEquals(3L, em.createQuery("select count(c) from CrewAssignment c where c.flight = :flight", Long.class)
                .setParameter("flight", payable).getSingleResult().longValue());
        assertEquals(0L, em.createQuery("select count(c) from CrewAssignment c where c.flight = :flight "
                        + "and c.flight.airline <> c.airlineEmployee.airline", Long.class)
                .setParameter("flight", payable).getSingleResult().longValue());

        User airportAdmin = user("airport.admin@mailsac.com");
        User inspector = user("airport.inspector@mailsac.com");
        assertEquals(User.Role.AIRPORT_EMPLOYEE, airportAdmin.getRole());
        assertEquals(AirportEmployee.AirportRole.INSPECTOR, airportEmployee(inspector).getAirportRole());
        assertEquals(ready.getOriginAirport().getId(), airportEmployee(inspector).getAirport().getId());
        assertEquals(airportEmployee(airportAdmin).getOperator().getId(),
                ready.getOriginAirport().getOperator().getId());

        User bahAdmin = user("atc.bah.admin@example.com");
        User dxbController = user("atc.dxb.controller@example.com");
        User dohAdmin = user("atc.doh.admin@example.com");
        assertEquals(User.Role.ATC_EMPLOYEE, bahAdmin.getRole());
        assertEquals(ATCEmployee.ATCRole.ADMIN, atcEmployee(bahAdmin).getAtcRole());
        assertEquals(ATCEmployee.ATCRole.CONTROLLER, atcEmployee(dxbController).getAtcRole());
        assertEquals(atcEmployee(bahAdmin).getFacility().getProvider().getId(),
                atcEmployee(dxbController).getFacility().getProvider().getId());
        assertNotEquals(atcEmployee(bahAdmin).getFacility().getProvider().getId(),
                atcEmployee(dohAdmin).getFacility().getProvider().getId());
        assertEquals(ready.getOriginAirport().getId(), atcEmployee(bahAdmin).getFacility().getAirport().getId());
        assertTrue(passwords.matches("TestPassword1", bahAdmin.getPassword()));

        MaintenanceRecord maintenance = em.createQuery("select m from MaintenanceRecord m where m.description = :description", MaintenanceRecord.class)
                .setParameter("description", "Project 3 sample routine maintenance").getSingleResult();
        User technician = user("maintenance.technician@mailsac.com");
        assertEquals(maintenance.getProvider().getId(), maintenanceEmployee(technician).getProvider().getId());
        assertTrue(maintenance.getProvider().getAirlines().stream()
                .anyMatch(a -> a.getId().equals(maintenance.getAirplane().getAirline().getId())));

        Payment payment = em.createQuery("select p from Payment p where p.booking.bookingRef = 'P3PAY01'", Payment.class).getSingleResult();
        assertEquals(payable.getId(), payment.getBooking().getFlight().getId());
        assertEquals(payable.getStandardPrice(), payment.getAmount());
        int seats = payable.getStandardSeatsCount();
        var departure = payable.getScheduledDeparture();
        var counts = counts();

        // Simulate edits: a rerun must not reset credentials, prices, or workflow state.
        airportAdmin.setPassword("changed-password-hash");
        bahAdmin.setPassword("changed-atc-password-hash");
        payable.setStandardPrice(47000L);
        payment.setStatus(Payment.PaymentStatus.PAID);
        maintenance.setStatus(MaintenanceRecord.MaintenanceStatus.COMPLETED);
        maintenance.setTechnician(maintenanceEmployee(technician));
        maintenance.setCompletedAt(java.time.LocalDateTime.now());
        seeder.run();
        em.flush();
        em.clear();

        assertEquals(counts, counts());
        assertEquals(seats, flight("GF-P3-PAY").getStandardSeatsCount());
        assertEquals(departure, flight("GF-P3-PAY").getScheduledDeparture());
        assertEquals(Long.valueOf(47000), flight("GF-P3-PAY").getStandardPrice());
        assertEquals("changed-password-hash", user("airport.admin@mailsac.com").getPassword());
        assertEquals("changed-atc-password-hash", user("atc.bah.admin@example.com").getPassword());
        assertEquals(Payment.PaymentStatus.PAID, em.find(Payment.class, payment.getId()).getStatus());
        assertEquals(MaintenanceRecord.MaintenanceStatus.COMPLETED,
                em.find(MaintenanceRecord.class, maintenance.getId()).getStatus());
        assertNotNull(em.find(Booking.class, 900001L));
        assertNotNull(em.find(Airplane.class, 900003L));
    }

    private Flight flight(String number) {
        return em.createQuery("select f from Flight f where f.flightNumber = :number", Flight.class)
                .setParameter("number", number).getSingleResult();
    }

    private User user(String email) {
        return em.createQuery("select u from User u where u.emailAddress = :email", User.class)
                .setParameter("email", email).getSingleResult();
    }

    private Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String entity : new String[]{"User", "Airline", "Airplane", "Flight", "Booking", "Payment",
                "CrewAssignment", "AirportOperator", "AirportEmployee", "FlightInspection",
                "MaintenanceProvider", "MaintenanceEmployee", "MaintenanceRecord",
                "AirNavigationServiceProvider", "ControlFacility", "ATCEmployee"}) {
            counts.put(entity, em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult());
        }
        return counts;
    }
}

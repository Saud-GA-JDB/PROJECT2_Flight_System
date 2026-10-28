package com.ga.saudsFlightSystem.config;

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
    @Autowired EntityManager em;
    @Autowired PasswordEncoder passwords;

    @Test
    public void seedsRelatedProjectThreeDataAndPreservesProgressOnRerun() {
        DatabaseSeeder seeder = new DatabaseSeeder(em, passwords);
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
        assertEquals(AirportEmployee.AirportRole.INSPECTOR, inspector.getAirportEmployee().getAirportRole());
        assertEquals(ready.getOriginAirport().getId(), inspector.getAirportEmployee().getAirport().getId());
        assertEquals(airportAdmin.getAirportEmployee().getOperator().getId(),
                ready.getOriginAirport().getOperator().getId());

        MaintenanceRecord maintenance = em.createQuery("select m from MaintenanceRecord m where m.description = :description", MaintenanceRecord.class)
                .setParameter("description", "Project 3 sample routine maintenance").getSingleResult();
        User technician = user("maintenance.technician@mailsac.com");
        assertEquals(maintenance.getProvider().getId(), technician.getMaintenanceEmployee().getProvider().getId());
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
        payable.setStandardPrice(47000L);
        payment.setStatus(Payment.PaymentStatus.PAID);
        maintenance.setStatus(MaintenanceRecord.MaintenanceStatus.COMPLETED);
        maintenance.setTechnician(technician.getMaintenanceEmployee());
        maintenance.setCompletedAt(java.time.LocalDateTime.now());
        seeder.run();
        em.flush();
        em.clear();

        assertEquals(counts, counts());
        assertEquals(seats, flight("GF-P3-PAY").getStandardSeatsCount());
        assertEquals(departure, flight("GF-P3-PAY").getScheduledDeparture());
        assertEquals(Long.valueOf(47000), flight("GF-P3-PAY").getStandardPrice());
        assertEquals("changed-password-hash", user("airport.admin@mailsac.com").getPassword());
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
                "MaintenanceProvider", "MaintenanceEmployee", "MaintenanceRecord"}) {
            counts.put(entity, em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult());
        }
        return counts;
    }
}

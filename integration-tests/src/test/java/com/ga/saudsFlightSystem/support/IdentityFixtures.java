package com.ga.saudsFlightSystem.support;

import com.ga.identity.model.User;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.security.CurrentActor;
import jakarta.persistence.EntityManager;

// Helpers for the existing in-process service fixtures; never packaged with the application.
public class IdentityFixtures {
    private static EntityManager em;

    public IdentityFixtures(EntityManager entityManager) {
        em = entityManager;
    }

    public static CurrentActor actor(User user) {
        return new CurrentActor(user.getId(), user.getRole().name(), user.getStatus().name(), user.isActive());
    }

    public static void linkEmployee(User user, BaseEntity employee) {
        if (user.getId() == null) em.persist(user);
        if (employee instanceof AirlineEmployee value) value.setUserId(user.getId());
        else if (employee instanceof AirportEmployee value) value.setUserId(user.getId());
        else if (employee instanceof MaintenanceEmployee value) value.setUserId(user.getId());
        else if (employee instanceof ATCEmployee value) value.setUserId(user.getId());
        em.persist(employee);
    }

    public static AirlineEmployee airlineEmployee(User user) { return employee(AirlineEmployee.class, user); }
    public static AirportEmployee airportEmployee(User user) { return employee(AirportEmployee.class, user); }
    public static MaintenanceEmployee maintenanceEmployee(User user) { return employee(MaintenanceEmployee.class, user); }
    public static ATCEmployee atcEmployee(User user) { return employee(ATCEmployee.class, user); }

    private static <T> T employee(Class<T> type, User user) {
        return em.createQuery("select e from " + type.getSimpleName() + " e where e.userId = :id", type)
                .setParameter("id", user.getId()).getResultStream().findFirst().orElse(null);
    }
}

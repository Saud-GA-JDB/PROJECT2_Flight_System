package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    PendingRegistration findByEmailAddress(String emailAddress);
    PendingRegistration findByCpr(String cpr);
    PendingRegistration findPendingRegistrationById(Long id);
}

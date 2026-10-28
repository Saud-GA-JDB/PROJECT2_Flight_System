package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.PendingRegistration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    PendingRegistration findByEmailAddress(String emailAddress);
    PendingRegistration findByCpr(String cpr);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.emailAddress = :email")
    PendingRegistration findByEmailAddressForUpdate(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p from PendingRegistration p
        where p.emailAddress = :email or p.cpr = :cpr
        order by p.id
        """)
    List<PendingRegistration> findByEmailAddressOrCprForUpdate(@Param("email") String email, @Param("cpr") String cpr);
}

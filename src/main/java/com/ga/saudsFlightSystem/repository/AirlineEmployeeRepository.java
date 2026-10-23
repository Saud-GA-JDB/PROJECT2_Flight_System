package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AirlineEmployee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AirlineEmployeeRepository extends JpaRepository<AirlineEmployee, Long> {
    boolean existsByCpr(String cpr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM AirlineEmployee e WHERE e.id = :employeeId")
    Optional<AirlineEmployee> findByIdForAssignment(@Param("employeeId") Long employeeId);
}

package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airplane;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AirplaneRepository extends JpaRepository<Airplane, Long> {
    boolean existsByRegistrationNumber(String registrationNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Airplane a WHERE a.id = :airplaneId")
    Optional<Airplane> findByIdForUpdate(@Param("airplaneId") Long airplaneId);
}

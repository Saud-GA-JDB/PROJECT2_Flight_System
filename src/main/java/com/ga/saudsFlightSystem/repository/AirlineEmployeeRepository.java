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

    @Query("""
    SELECT e FROM AirlineEmployee e
    LEFT JOIN FETCH e.airline
    WHERE e.userId = :userId
    """)
    Optional<AirlineEmployee> findByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM AirlineEmployee e WHERE e.id = :employeeId")
    Optional<AirlineEmployee> findByIdForAssignment(@Param("employeeId") Long employeeId);

    @Query("""
    SELECT e FROM AirlineEmployee e
    JOIN FETCH e.airline
    WHERE e.userId = :userId
      AND e.airlineRole = :role
    """)
    Optional<AirlineEmployee> findByUserIdAndRole(@Param("userId") Long userId, @Param("role") AirlineEmployee.AirlineRole role);

    @Query("""
    SELECT e.userId FROM AirlineEmployee e
    WHERE e.id = :employeeId
    """)
    Optional<Long> findUserIdByEmployeeId(@Param("employeeId") Long employeeId);
}

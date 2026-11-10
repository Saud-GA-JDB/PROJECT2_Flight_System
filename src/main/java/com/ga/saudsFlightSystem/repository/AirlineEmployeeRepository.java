package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AirlineEmployee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface AirlineEmployeeRepository extends JpaRepository<AirlineEmployee, Long> {

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

    @Query(value = """
        SELECT DISTINCT e.user_id
        FROM airline_employees e
        WHERE e.user_id IS NOT NULL
          AND (
            (e.airline_id = :airlineId AND e.airline_role = 'ADMIN')
            OR (
                e.airline_role = 'PILOT'
                AND EXISTS (
                    SELECT 1
                    FROM crew_assignments c
                    WHERE c.flight_id = :flightId
                      AND c.employee_id = e.id
                      AND c.duty IN ('PILOT', 'SECONDARY_PILOT')
                )
            )
          )
        """, nativeQuery = true)
    List<Long> findFlightRecipientUserIds(@Param("flightId") Long flightId, @Param("airlineId") Long airlineId);

    @Query(value = """
        SELECT e.user_id
        FROM airline_employees e
        WHERE e.airline_id = :airlineId
          AND e.airline_role = 'ADMIN'
          AND e.user_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> findAdminUserIdsByAirlineId(@Param("airlineId") Long airlineId);
}

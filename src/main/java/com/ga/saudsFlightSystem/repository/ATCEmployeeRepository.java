package com.ga.saudsFlightSystem.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.ga.saudsFlightSystem.model.ATCEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ATCEmployeeRepository extends JpaRepository<ATCEmployee, Long> {

    boolean existsByEmployeeNumber(String employeeNumber);

    @Query("""
        SELECT e FROM ATCEmployee e
        LEFT JOIN FETCH e.facility f
        LEFT JOIN FETCH f.airport
        LEFT JOIN FETCH f.provider
        WHERE e.userId = :userId
        """)
    Optional<ATCEmployee> findByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT e.user_id
        FROM atc_employees e
        WHERE e.facility_id = :facilityId
          AND e.atc_role IN ('ADMIN', 'CONTROLLER')
          AND e.user_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> findClearanceRecipientUserIds(@Param("facilityId") Long facilityId);

    @Query(value = """
        SELECT DISTINCT e.user_id
        FROM atc_employees e
        JOIN control_facilities cf ON cf.id = e.facility_id
        WHERE e.user_id IS NOT NULL
          AND e.atc_role IN ('ADMIN', 'CONTROLLER')
          AND EXISTS (
              SELECT 1
              FROM flights f
              WHERE f.id = :flightId
                AND (
                    cf.airport_id = f.origin_airport_id
                    OR cf.airport_id = f.destination_airport_id
                )
          )
        """, nativeQuery = true)
    List<Long> findFlightActionRecipientUserIds(@Param("flightId") Long flightId);

    @Query(value = """
        SELECT e.*
        FROM atc_employees e
        WHERE e.facility_id = :facilityId
          AND e.atc_role = 'CONTROLLER'
          AND e.user_id IS NOT NULL
        ORDER BY e.id
        """, nativeQuery = true)
    List<ATCEmployee> findControllersByFacilityId(@Param("facilityId") Long facilityId);
}

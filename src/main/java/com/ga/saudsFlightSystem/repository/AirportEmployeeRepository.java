package com.ga.saudsFlightSystem.repository;

import java.util.List;

import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.ga.saudsFlightSystem.model.AirportEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportEmployeeRepository extends JpaRepository<AirportEmployee, Long> {

    boolean existsByAirport_Id(Long airportId);

    @Query("""
        SELECT e FROM AirportEmployee e
        LEFT JOIN FETCH e.airport a
        LEFT JOIN FETCH a.operator
        LEFT JOIN FETCH e.operator
        WHERE e.userId = :userId
        """)
    Optional<AirportEmployee> findByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT e FROM AirportEmployee e
        WHERE e.airport.id = :airportId AND e.operator.id = :operatorId AND e.userId IS NOT NULL
          AND (:role IS NULL OR e.airportRole = :role)
        ORDER BY e.id ASC
        """)
    List<AirportEmployee> findEmployeesByAirportId(@Param("airportId") Long airportId,
            @Param("operatorId") Long operatorId, @Param("role") AirportEmployee.AirportRole role);
}

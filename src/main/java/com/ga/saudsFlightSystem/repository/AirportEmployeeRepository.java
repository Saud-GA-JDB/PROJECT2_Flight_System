package com.ga.saudsFlightSystem.repository;

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
}

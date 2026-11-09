package com.ga.saudsFlightSystem.repository;

import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.ga.saudsFlightSystem.model.AirportEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportEmployeeRepository extends JpaRepository<AirportEmployee, Long> {
    boolean existsByCpr(String cpr);

    boolean existsByAirport_Id(Long airportId);

    @Query("""
        SELECT e FROM User u JOIN u.airportEmployee e
        LEFT JOIN FETCH e.airport a
        LEFT JOIN FETCH a.operator
        LEFT JOIN FETCH e.operator
        WHERE u.id = :userId
        """)
    Optional<AirportEmployee> findByUserId(@Param("userId") Long userId);
}

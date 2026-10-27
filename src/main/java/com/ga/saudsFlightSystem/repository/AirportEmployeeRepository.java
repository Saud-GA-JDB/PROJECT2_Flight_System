package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AirportEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportEmployeeRepository extends JpaRepository<AirportEmployee, Long> {
    boolean existsByCpr(String cpr);

    boolean existsByAirport_Id(Long airportId);
}

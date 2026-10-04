package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, Long> {
    boolean existsByIataCode(String iataId);
    Airport findByIataCode(String iataCode);
}

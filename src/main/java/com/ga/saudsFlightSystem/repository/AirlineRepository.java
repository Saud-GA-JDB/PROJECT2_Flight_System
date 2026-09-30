package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AirlineRepository extends JpaRepository<Airline, Long> {
    Optional<Airline> findByAirlineCode(String airlineCode);
    Optional<Airline> findByName(String airlineName);
}

package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Flight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    List<Flight> findByStatus(Flight.FlightStatus status);
}

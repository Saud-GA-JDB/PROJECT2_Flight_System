package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Flight;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightRepository extends JpaRepository<Flight, Long> {

}

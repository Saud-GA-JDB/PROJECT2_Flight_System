package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.FlightInspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlightInspectionRepository extends JpaRepository<FlightInspection, Long> {

    Optional<FlightInspection> findFirstByFlight_IdOrderByIdDesc(Long flightId);
    List<FlightInspection> findByFlight_IdOrderByIdDesc(Long flightId);
    List<FlightInspection> findByAirport_IdOrderByIdDesc(Long airportId);
    List<FlightInspection> findByInspector_IdOrderByIdDesc(Long inspectorId);
}

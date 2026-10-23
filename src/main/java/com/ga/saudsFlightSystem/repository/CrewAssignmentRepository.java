package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.CrewAssignment;
import com.ga.saudsFlightSystem.model.Flight;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, Long> {
    List<CrewAssignment> findByFlightId(Long flightId);
    List<CrewAssignment> findByAirlineEmployeeId(Long employeeId);

    boolean existsByFlightIdAndAirlineEmployeeId(Long flightId, Long employeeId);
    @Query("""
    SELECT COUNT(c) > 0 FROM CrewAssignment c
    WHERE c.airlineEmployee.id = :employeeId
      AND c.flight.status <> :excludedStatus
      AND c.flight.scheduledDeparture < :scheduledArrival
      AND c.flight.scheduledArrival > :scheduledDeparture
    """)
    boolean hasOverlappingAssignment(
            @Param("employeeId") Long employeeId,
            @Param("scheduledDeparture") LocalDateTime scheduledDeparture,
            @Param("scheduledArrival") LocalDateTime scheduledArrival,
            @Param("excludedStatus") Flight.FlightStatus excludedStatus
    );

}

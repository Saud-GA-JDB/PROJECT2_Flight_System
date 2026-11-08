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
    List<CrewAssignment> findByFlightIdOrderByIdAsc(Long flightId);

    @Query(value = """
        SELECT COUNT(*) > 0 FROM crew_assignments
        WHERE flight_id = :flightId AND employee_id = :employeeId
          AND duty IN ('PILOT', 'SECONDARY_PILOT')
        """, nativeQuery = true)
    boolean isAssignedPilot(@Param("flightId") Long flightId, @Param("employeeId") Long employeeId);

    @Query(value = """
        SELECT COUNT(*) > 0 FROM crew_assignments c JOIN flights f ON f.id = c.flight_id
        WHERE f.id <> :flightId AND c.employee_id IN :employeeIds
          AND ((f.actual_departure IS NOT NULL AND f.actual_arrival IS NULL) OR f.status = 'IN_AIR')
        """, nativeQuery = true)
    boolean hasOccupiedCrew(@Param("flightId") Long flightId, @Param("employeeIds") List<Long> employeeIds);

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

    Optional<CrewAssignment> findByFlightIdAndAirlineEmployeeId(Long flightId, Long employeeId);

}

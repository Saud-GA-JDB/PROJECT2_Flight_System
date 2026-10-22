package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.CrewAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, Long> {
    List<CrewAssignment> findByFlightId(Long flightId);
    List<CrewAssignment> findByAirlineEmployeeId(Long employeeId);

    boolean existsByFlightIdAndAirlineEmployeeId(Long flightId, Long employeeId);
}

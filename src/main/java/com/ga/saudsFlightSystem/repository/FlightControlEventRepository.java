package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.FlightControlEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FlightControlEventRepository extends JpaRepository<FlightControlEvent, Long> {
    @Query(value = """
        SELECT e.* FROM flight_control_events e WHERE e.flight_id = :flightId
          AND (CAST(:facilityId AS bigint) IS NULL OR e.facility_id = :facilityId)
          AND (CAST(:operation AS text) IS NULL OR e.operation = :operation)
        ORDER BY e.id DESC
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<FlightControlEvent> findHistory(@Param("flightId") Long flightId, @Param("facilityId") Long facilityId,
                                       @Param("operation") String operation, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM flight_control_events e WHERE e.flight_id = :flightId
          AND (CAST(:facilityId AS bigint) IS NULL OR e.facility_id = :facilityId)
          AND (CAST(:operation AS text) IS NULL OR e.operation = :operation)
        """, nativeQuery = true)
    long countHistory(@Param("flightId") Long flightId, @Param("facilityId") Long facilityId,
                                       @Param("operation") String operation);
}

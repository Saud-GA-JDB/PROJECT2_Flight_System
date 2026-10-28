package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Flight;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT f FROM Flight f WHERE f.id = :flightId")
    Optional<Flight> findByIdForCrewRead(@Param("flightId") Long flightId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flight f WHERE f.id = :flightId")
    Optional<Flight> findByIdForCrewWrite(@Param("flightId") Long flightId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Flight f WHERE f.id = :flightId")
    Optional<Flight> findByIdForUpdate(@Param("flightId") Long flightId);

    List<Flight> findByStatus(Flight.FlightStatus status);

    List<Flight> findByAirplane_Id(Long airplaneId);

    @Query("""
        SELECT f.id FROM Flight f
        WHERE f.status = :activeStatus OR f.status = :inAirStatus
        ORDER BY f.id
        """)
    List<Long> findIdsByStatuses(
            @Param("activeStatus") Flight.FlightStatus activeStatus,
            @Param("inAirStatus") Flight.FlightStatus inAirStatus);

    @Query(value = """
    SELECT f.*
    FROM flights f
    JOIN airline a ON a.id = f.airline_id
    JOIN airplanes p ON p.id = f.airplane_id
    JOIN airports o ON o.id = f.origin_airport_id
    JOIN airports d ON d.id = f.destination_airport_id
    WHERE f.status = 'ACTIVE'
      AND p.status = 'ACTIVE'
      AND f.scheduled_departure > :bookingCutoff
      AND (f.first_class_seats_count > 0 OR f.standard_seats_count > 0)
      AND (CAST(:startDate AS timestamp) IS NULL OR f.scheduled_departure >= :startDate)
      AND (CAST(:endDate AS timestamp) IS NULL OR f.scheduled_departure < :endDate)
      AND (CAST(:airlineCode AS text) IS NULL OR LOWER(a.airline_code) = LOWER(:airlineCode))
      AND (CAST(:originAirport AS text) IS NULL OR LOWER(o.iata_code) = LOWER(:originAirport))
      AND (CAST(:destinationAirport AS text) IS NULL OR LOWER(d.iata_code) = LOWER(:destinationAirport))
      AND (CAST(:originCity AS text) IS NULL OR LOWER(o.city) = LOWER(:originCity))
      AND (CAST(:destinationCity AS text) IS NULL OR LOWER(d.city) = LOWER(:destinationCity))
      AND (CAST(:originCountry AS text) IS NULL OR LOWER(o.country) = LOWER(:originCountry))
      AND (CAST(:destinationCountry AS text) IS NULL OR LOWER(d.country) = LOWER(:destinationCountry))
      AND (CAST(:seatType AS text) IS NULL
           OR (:seatType = 'firstClass' AND f.first_class_seats_count > 0)
           OR (:seatType = 'standard' AND f.standard_seats_count > 0))
    ORDER BY
      CASE WHEN :sortField = 'scheduledDeparture' AND :sortDirection = 'asc' THEN f.scheduled_departure END ASC,
      CASE WHEN :sortField = 'scheduledDeparture' AND :sortDirection = 'desc' THEN f.scheduled_departure END DESC,
      CASE WHEN :sortField = 'scheduledArrival' AND :sortDirection = 'asc' THEN f.scheduled_arrival END ASC,
      CASE WHEN :sortField = 'scheduledArrival' AND :sortDirection = 'desc' THEN f.scheduled_arrival END DESC,
      CASE WHEN :sortField = 'flightNumber' AND :sortDirection = 'asc' THEN f.flight_number END ASC,
      CASE WHEN :sortField = 'flightNumber' AND :sortDirection = 'desc' THEN f.flight_number END DESC,
      f.id ASC
    LIMIT :size OFFSET :offset
    """, nativeQuery = true)
    List<Flight> searchFlights(
            @Param("bookingCutoff") LocalDateTime bookingCutoff,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("airlineCode") String airlineCode,
            @Param("originAirport") String originAirport,
            @Param("destinationAirport") String destinationAirport,
            @Param("originCity") String originCity,
            @Param("destinationCity") String destinationCity,
            @Param("originCountry") String originCountry,
            @Param("destinationCountry") String destinationCountry,
            @Param("seatType") String seatType,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection,
            @Param("size") int size,
            @Param("offset") long offset
    );

    @Query(value = """
    SELECT COUNT(*)
    FROM flights f
    JOIN airline a ON a.id = f.airline_id
    JOIN airplanes p ON p.id = f.airplane_id
    JOIN airports o ON o.id = f.origin_airport_id
    JOIN airports d ON d.id = f.destination_airport_id
    WHERE f.status = 'ACTIVE'
      AND p.status = 'ACTIVE'
      AND f.scheduled_departure > :bookingCutoff
      AND (f.first_class_seats_count > 0 OR f.standard_seats_count > 0)
      AND (CAST(:startDate AS timestamp) IS NULL OR f.scheduled_departure >= :startDate)
      AND (CAST(:endDate AS timestamp) IS NULL OR f.scheduled_departure < :endDate)
      AND (CAST(:airlineCode AS text) IS NULL OR LOWER(a.airline_code) = LOWER(:airlineCode))
      AND (CAST(:originAirport AS text) IS NULL OR LOWER(o.iata_code) = LOWER(:originAirport))
      AND (CAST(:destinationAirport AS text) IS NULL OR LOWER(d.iata_code) = LOWER(:destinationAirport))
      AND (CAST(:originCity AS text) IS NULL OR LOWER(o.city) = LOWER(:originCity))
      AND (CAST(:destinationCity AS text) IS NULL OR LOWER(d.city) = LOWER(:destinationCity))
      AND (CAST(:originCountry AS text) IS NULL OR LOWER(o.country) = LOWER(:originCountry))
      AND (CAST(:destinationCountry AS text) IS NULL OR LOWER(d.country) = LOWER(:destinationCountry))
      AND (CAST(:seatType AS text) IS NULL
           OR (:seatType = 'firstClass' AND f.first_class_seats_count > 0)
           OR (:seatType = 'standard' AND f.standard_seats_count > 0))
    """, nativeQuery = true)
    long countSearchFlights(
            @Param("bookingCutoff") LocalDateTime bookingCutoff,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("airlineCode") String airlineCode,
            @Param("originAirport") String originAirport,
            @Param("destinationAirport") String destinationAirport,
            @Param("originCity") String originCity,
            @Param("destinationCity") String destinationCity,
            @Param("originCountry") String originCountry,
            @Param("destinationCountry") String destinationCountry,
            @Param("seatType") String seatType
    );
}

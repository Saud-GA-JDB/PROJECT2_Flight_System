package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.request.response.AirlineFlightResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface FlightRepository extends JpaRepository<Flight, Long> {
    @Query(value = """
        SELECT f.id FROM flights f
        JOIN airports a ON a.id = f.origin_airport_id
        WHERE f.origin_airport_id = :airportId AND a.operator_id = :operatorId
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        ORDER BY f.scheduled_departure DESC, f.id DESC
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Long> findAirportFlightIds(@Param("airportId") Long airportId, @Param("operatorId") Long operatorId,
            @Param("status") String status, @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM flights f
        JOIN airports a ON a.id = f.origin_airport_id
        WHERE f.origin_airport_id = :airportId AND a.operator_id = :operatorId
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        """, nativeQuery = true)
    long countAirportFlights(@Param("airportId") Long airportId, @Param("operatorId") Long operatorId,
            @Param("status") String status, @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @Query("""
        SELECT new com.ga.saudsFlightSystem.model.request.response.AirlineFlightResponse(
            f.id, f.flightNumber, airline.id,
            airplane.id, airplane.registrationNumber,
            origin.id, origin.iataCode,
            destination.id, destination.iataCode,
            f.scheduledDeparture, f.scheduledArrival, f.actualDeparture, f.actualArrival,
            f.status, f.firstClassSeatsCount, f.standardSeatsCount,
            f.firstClassPrice, f.standardPrice, f.currency)
        FROM Flight f
        LEFT JOIN f.airline airline
        LEFT JOIN f.airplane airplane
        JOIN f.originAirport origin
        LEFT JOIN f.destinationAirport destination
        WHERE origin.id = :airportId AND origin.operator.id = :operatorId AND f.id IN :ids
        ORDER BY f.scheduledDeparture DESC, f.id DESC
        """)
    List<AirlineFlightResponse> findAirportFlightsWithDetails(@Param("airportId") Long airportId,
            @Param("operatorId") Long operatorId, @Param("ids") List<Long> ids);

    interface AirlineDashboardDay {
        LocalDate getDate();
        long getFlightCount();
        long getBookingCount();
        long getBookedBookings();
        long getCancelledBookings();
        long getFinishedBookings();
        long getOccupiedSeats();
        long getSeatCapacity();
    }

    @Query(value = """
        WITH flight_bookings AS (
            SELECT f.id, CAST(f.scheduled_departure AS date) AS departure_date,
                   f.status, f.standard_seats_count, f.first_class_seats_count,
                   COUNT(b.id) AS booking_count,
                   COUNT(b.id) FILTER (WHERE b.status = 'BOOKED') AS booked_bookings,
                   COUNT(b.id) FILTER (WHERE b.status = 'CANCELLED') AS cancelled_bookings,
                   COUNT(b.id) FILTER (WHERE b.status = 'FINISHED') AS finished_bookings
            FROM flights f
            LEFT JOIN bookings b ON b.flight_id = f.id
            WHERE f.airline_id = :airlineId
              AND f.scheduled_departure >= :fromDate
              AND f.scheduled_departure < :toDate
            GROUP BY f.id
        )
        SELECT departure_date AS "date",
               COUNT(*) AS "flightCount",
               SUM(booking_count) AS "bookingCount",
               SUM(booked_bookings) AS "bookedBookings",
               SUM(cancelled_bookings) AS "cancelledBookings",
               SUM(finished_bookings) AS "finishedBookings",
               SUM(CASE WHEN status <> 'CANCELLED'
                   THEN booked_bookings + finished_bookings ELSE 0 END) AS "occupiedSeats",
               SUM(CASE WHEN status <> 'CANCELLED'
                   THEN standard_seats_count::bigint + first_class_seats_count
                        + booked_bookings + finished_bookings
                   ELSE 0 END) AS "seatCapacity"
        FROM flight_bookings
        GROUP BY departure_date
        ORDER BY departure_date
        """, nativeQuery = true)
    List<AirlineDashboardDay> findAirlineDashboardDays(@Param("airlineId") Long airlineId,
            @Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query(value = "SELECT airplane_id FROM flights WHERE id = :flightId", nativeQuery = true)
    Long findAirplaneIdForOperation(@Param("flightId") Long flightId);

    @Query(value = """
        SELECT COUNT(*) > 0 FROM flights
        WHERE id <> :flightId AND airplane_id = :airplaneId
          AND ((actual_departure IS NOT NULL AND actual_arrival IS NULL) OR status = 'IN_AIR')
        """, nativeQuery = true)
    boolean hasOccupiedAirplane(@Param("flightId") Long flightId, @Param("airplaneId") Long airplaneId);

    @Query(value = """
        SELECT f.* FROM flights f WHERE EXISTS (
            SELECT 1 FROM crew_assignments c WHERE c.flight_id = f.id
              AND c.employee_id = :employeeId AND c.duty IN ('PILOT', 'SECONDARY_PILOT'))
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        ORDER BY f.scheduled_departure ASC, f.id ASC
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Flight> findPilotOperations(@Param("employeeId") Long employeeId,
            @Param("status") String status, @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM flights f WHERE EXISTS (
            SELECT 1 FROM crew_assignments c WHERE c.flight_id = f.id
              AND c.employee_id = :employeeId AND c.duty IN ('PILOT', 'SECONDARY_PILOT'))
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        """, nativeQuery = true)
    long countPilotOperations(@Param("employeeId") Long employeeId,
            @Param("status") String status, @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @Query(value = """
        SELECT f.* FROM flights f WHERE ((CAST(:operation AS text) IS NULL AND (f.origin_airport_id = :airportId OR f.destination_airport_id = :airportId))
          OR (:operation = 'DEPARTURE' AND f.origin_airport_id = :airportId)
          OR (:operation = 'ARRIVAL' AND f.destination_airport_id = :airportId))
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        ORDER BY f.scheduled_departure ASC, f.id ASC
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Flight> findTowerOperations(@Param("airportId") Long airportId, @Param("operation") String operation,
            @Param("status") String status, @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM flights f WHERE ((CAST(:operation AS text) IS NULL AND (f.origin_airport_id = :airportId OR f.destination_airport_id = :airportId))
          OR (:operation = 'DEPARTURE' AND f.origin_airport_id = :airportId)
          OR (:operation = 'ARRIVAL' AND f.destination_airport_id = :airportId))
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        """, nativeQuery = true)
    long countTowerOperations(@Param("airportId") Long airportId, @Param("operation") String operation,
            @Param("status") String status, @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @Query(value = """
        SELECT f.* FROM flights f WHERE f.clearance_facility_id = :facilityId AND f.clearance_status = 'PENDING'
          AND (CAST(:operation AS text) IS NULL OR f.clearance_operation = :operation)
        ORDER BY f.id ASC
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Flight> findPendingClearances(@Param("facilityId") Long facilityId, @Param("operation") String operation, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM flights f WHERE f.clearance_facility_id = :facilityId AND f.clearance_status = 'PENDING'
          AND (CAST(:operation AS text) IS NULL OR f.clearance_operation = :operation)
        """, nativeQuery = true)
    long countPendingClearances(@Param("facilityId") Long facilityId, @Param("operation") String operation);

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
      AND f.actual_departure IS NULL
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
      AND f.actual_departure IS NULL
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

    @Query(value = """
        SELECT f.id FROM flights f
        WHERE f.airline_id = :airlineId
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        ORDER BY f.scheduled_departure DESC, f.id DESC
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Long> findAirlineFlightIds(@Param("airlineId") Long airlineId, @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate,
            @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM flights f
        WHERE f.airline_id = :airlineId
          AND (CAST(:status AS text) IS NULL OR f.status = :status)
          AND (CAST(:fromDate AS timestamp) IS NULL OR f.scheduled_departure >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR f.scheduled_departure < :toDate)
        """, nativeQuery = true)
    long countAirlineFlights(@Param("airlineId") Long airlineId, @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);

    @Query("""
        SELECT f FROM Flight f
        LEFT JOIN FETCH f.airplane
        LEFT JOIN FETCH f.originAirport
        LEFT JOIN FETCH f.destinationAirport
        WHERE f.airline.id = :airlineId AND f.id IN :ids
        ORDER BY f.scheduledDeparture DESC, f.id DESC
        """)
    List<Flight> findAirlineFlightsWithDetails(@Param("airlineId") Long airlineId, @Param("ids") List<Long> ids);
}

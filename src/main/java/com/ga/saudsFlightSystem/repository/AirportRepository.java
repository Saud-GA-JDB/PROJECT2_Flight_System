package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AirportRepository extends JpaRepository<Airport, Long> {
    boolean existsByIataCode(String iataId);
    Airport findByIataCode(String iataCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Airport a WHERE a.id = :airportId")
    Optional<Airport> findByIdForUpdate(@Param("airportId") Long airportId);
}

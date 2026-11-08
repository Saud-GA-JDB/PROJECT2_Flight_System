package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.ControlFacility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ControlFacilityRepository extends JpaRepository<ControlFacility, Long> {
    boolean existsByAirport_Id(Long airportId);

    Optional<ControlFacility> findByAirport_Id(Long airportId);

    @Query(value = "SELECT * FROM control_facilities WHERE provider_id = :providerId ORDER BY id LIMIT :size OFFSET :offset", nativeQuery = true)
    List<ControlFacility> findByProviderId(@Param("providerId") Long providerId, @Param("size") int size, @Param("offset") long offset);

    long countByProvider_Id(Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM ControlFacility f WHERE f.id = :facilityId")
    Optional<ControlFacility> findByIdForUpdate(@Param("facilityId") Long facilityId);
}

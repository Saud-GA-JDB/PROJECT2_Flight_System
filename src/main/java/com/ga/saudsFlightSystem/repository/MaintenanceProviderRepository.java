package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.MaintenanceProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MaintenanceProviderRepository extends JpaRepository<MaintenanceProvider, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MaintenanceProvider p WHERE p.id = :providerId")
    Optional<MaintenanceProvider> findByIdForUpdate(@Param("providerId") Long providerId);
}

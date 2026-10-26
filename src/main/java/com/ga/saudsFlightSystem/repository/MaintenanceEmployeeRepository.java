package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.MaintenanceEmployee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaintenanceEmployeeRepository extends JpaRepository<MaintenanceEmployee, Long> {
    List<MaintenanceEmployee> findByProviderId(Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM MaintenanceEmployee e WHERE e.id = :employeeId")
    Optional<MaintenanceEmployee> findByIdForUpdate(@Param("employeeId") Long employeeId);
}

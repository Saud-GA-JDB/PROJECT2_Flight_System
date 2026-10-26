package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.MaintenanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByAirplaneIdOrderByCreatedAtDesc(Long airplaneId);
    List<MaintenanceRecord> findByTechnicianIdOrderByCreatedAtDesc(Long technicianId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MaintenanceRecord r WHERE r.id = :recordId")
    Optional<MaintenanceRecord> findByIdForUpdate(@Param("recordId") Long recordId);
}

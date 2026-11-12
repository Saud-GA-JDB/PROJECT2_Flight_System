package com.ga.maintenance.repository;

import com.ga.maintenance.model.MaintenanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {
    @Query("SELECT r.airplaneId FROM MaintenanceRecord r WHERE r.id = :recordId")
    Optional<Long> findAirplaneIdByRecordId(@Param("recordId") Long recordId);

    @Query(value = """
        SELECT r.* FROM maintenance_records r
        WHERE r.airplane_id = :airplaneId
          AND (CAST(:status AS text) IS NULL OR r.status = :status)
        ORDER BY r.created_at DESC, r.id DESC LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<MaintenanceRecord> findAirlineHistory(@Param("airplaneId") Long airplaneId,
            @Param("status") String status, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM maintenance_records r
        WHERE r.airplane_id = :airplaneId
          AND (CAST(:status AS text) IS NULL OR r.status = :status)
        """, nativeQuery = true)
    long countAirlineHistory(@Param("airplaneId") Long airplaneId,
                            @Param("status") String status);

    List<MaintenanceRecord> findByAirplaneIdOrderByCreatedAtDesc(Long airplaneId);
    List<MaintenanceRecord> findByTechnicianIdOrderByCreatedAtDesc(Long technicianId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MaintenanceRecord r WHERE r.id = :recordId")
    Optional<MaintenanceRecord> findByIdForUpdate(@Param("recordId") Long recordId);
    List<MaintenanceRecord> findByProviderIdOrderByCreatedAtDesc(Long providerId);
}

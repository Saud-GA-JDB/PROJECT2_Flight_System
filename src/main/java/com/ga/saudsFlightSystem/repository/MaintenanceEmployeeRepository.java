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

    @Query("""
        SELECT e FROM MaintenanceEmployee e
        LEFT JOIN FETCH e.provider
        WHERE e.userId = :userId
        """)
    Optional<MaintenanceEmployee> findByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT DISTINCT e.user_id
        FROM maintenance_employees e
        WHERE e.user_id IS NOT NULL
          AND (
            (e.provider_id = :providerId AND e.maintenance_role = 'ADMIN')
            OR (
                e.maintenance_role = 'TECHNICIAN'
                AND EXISTS (
                    SELECT 1
                    FROM maintenance_records r
                    WHERE r.id = :recordId
                      AND r.technician_id = e.id
                )
            )
          )
        """, nativeQuery = true)
    List<Long> findCancellationRecipientUserIds(@Param("recordId") Long recordId, @Param("providerId") Long providerId);
}

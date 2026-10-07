package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}

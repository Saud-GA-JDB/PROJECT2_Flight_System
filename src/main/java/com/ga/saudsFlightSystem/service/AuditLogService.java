package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.repository.AuditLogRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class AuditLogService {
    private AuditLogRepository auditLogRepository;

    public void addAuditLog(User user, AuditLog.Action action,
                            AuditLog.EntityType entityType, Long entityId,
                            String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        if (user != null) {
            auditLog.setUserId(user.getId());
            auditLog.setUserRole(user.getRole());
        }

        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDescription(description);

        auditLogRepository.save(auditLog);
    }
}

package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.repository.AuditLogRepository;
import com.ga.saudsFlightSystem.security.CurrentActor;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class AuditLogService {
    private AuditLogRepository auditLogRepository;

    public void addAuditLog(CurrentActor actor, AuditLog.Action action, AuditLog.EntityType entityType, Long entityId, String description) {
        // Scheduled system actions have no logged-in user.
        saveAuditLog(actor == null ? null : actor.userId(), actor == null ? null : actor.role(),
                action, entityType, entityId, description);
    }

    public void addAuditLogForActor(CurrentActor actor, AuditLog.Action action, AuditLog.EntityType entityType, Long entityId, String description) {
        saveAuditLog(actor.userId(), actor.role(), action, entityType, entityId, description);
    }

    private void saveAuditLog(Long userId, String userRole, AuditLog.Action action,
                              AuditLog.EntityType entityType, Long entityId, String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        auditLog.setUserId(userId);
        auditLog.setUserRole(userRole);

        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDescription(description);

        auditLogRepository.save(auditLog);
    }
}

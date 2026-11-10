package com.ga.identity.service;

import com.ga.identity.model.AuditLog;
import com.ga.identity.model.User;
import com.ga.identity.repository.AuditLogRepository;
import com.ga.identity.security.CurrentActor;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class AuditLogService {
    private AuditLogRepository auditLogRepository;

    public void addAuditLog(User user, AuditLog.Action action, AuditLog.EntityType entityType, Long entityId, String description) {
        // Scheduled system actions have no logged-in user.
        saveAuditLog(user == null ? null : user.getId(), user == null ? null : user.getRole(),
                action, entityType, entityId, description);
    }

    public void addAuditLogForActor(CurrentActor actor, AuditLog.Action action, AuditLog.EntityType entityType, Long entityId, String description) {
        saveAuditLog(actor.userId(), User.Role.valueOf(actor.role()), action, entityType, entityId, description);
    }

    private void saveAuditLog(Long userId, User.Role userRole, AuditLog.Action action,
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

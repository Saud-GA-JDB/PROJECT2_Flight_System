package com.ga.maintenance.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity(name = "MaintenanceAuditLog")
@Table(name = "maintenance_audit_logs")
@Data
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Long userId;
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityType entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 500)
    private String description;

    public enum Action {
        MAINTENANCE_PROVIDER_CREATED, MAINTENANCE_PROVIDER_LINKED,
        MAINTENANCE_EMPLOYEE_CREATED, MAINTENANCE_RECORD_CREATED,
        MAINTENANCE_TECHNICIAN_ASSIGNED, MAINTENANCE_RECORD_COMPLETED, MAINTENANCE_CANCELLED
    }

    public enum EntityType {
        USER, MAINTENANCE_PROVIDER, MAINTENANCE_RECORD
    }
}

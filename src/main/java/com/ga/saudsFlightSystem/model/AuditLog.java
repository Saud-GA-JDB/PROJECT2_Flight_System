package com.ga.saudsFlightSystem.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private Long userId;

    @Column
    @Enumerated(EnumType.STRING)
    private User.Role userRole;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Action action;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 500)
    private String description;

    public enum Action {
        BOOKING_CREATED, BOOKING_CANCELLED,
        USER_REGISTERED, USER_SETUP_COMPLETED,
        PROFILE_UPDATED, USER_DEACTIVATED,
        PASSWORD_CHANGED, PASSWORD_RESET,
        AIRLINE_CREATED, AIRLINE_ADMIN_CREATED,
        AIRPLANE_CREATED, AIRPLANE_ACTIVATION_REQUESTED,
        AIRPLANE_ACTIVATION_ACCEPTED, AIRPLANE_ACTIVATION_DENIED,
        FLIGHT_CREATED, FLIGHT_STATUS_CHANGED
    }

    public enum EntityType {
        BOOKING, USER, AIRLINE, AIRPLANE, AIRPLANE_REQUEST, FLIGHT
    }
}

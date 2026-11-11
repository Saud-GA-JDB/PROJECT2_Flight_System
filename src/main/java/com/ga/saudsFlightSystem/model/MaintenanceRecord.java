package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.Instant;

@Data
@Entity
@Table(name = "maintenance_records")
public class MaintenanceRecord {

    public enum MaintenanceStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "airplane_id", nullable = false)
    @JsonIgnore
    private Long airplaneId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    @JsonIgnore
    private MaintenanceEmployee technician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private MaintenanceProvider provider;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String findings;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    @Column(length = 2000)
    private String cancellationReason;

    private Instant cancelledAt;

    @Column(name = "cancelled_by_user_id")
    @JsonIgnore
    private Long cancelledByUserId;
}

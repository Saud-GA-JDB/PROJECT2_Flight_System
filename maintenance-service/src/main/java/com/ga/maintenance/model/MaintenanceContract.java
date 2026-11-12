package com.ga.maintenance.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(
        name = "airline_maintenance_providers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider_id", "airline_id"})
)
public class MaintenanceContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "airline_id", nullable = false)
    private Long airlineId;
}

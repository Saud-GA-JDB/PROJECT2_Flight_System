package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "maintenance_employees")
public class MaintenanceEmployee extends Person {

    public enum MaintenanceRole {
        ADMIN,
        TECHNICIAN
    }

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MaintenanceRole maintenanceRole;

    @Column
    private String licenseNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private MaintenanceProvider provider;
}

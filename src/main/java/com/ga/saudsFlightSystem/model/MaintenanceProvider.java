package com.ga.saudsFlightSystem.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "maintenance_providers")
public class MaintenanceProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contactEmail;

}

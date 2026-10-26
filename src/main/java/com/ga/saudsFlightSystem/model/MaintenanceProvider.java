package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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

    @ManyToMany
    @JoinTable(
            name = "airline_maintenance_providers",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "airline_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"provider_id", "airline_id"})
    )
    @JsonIgnore
    private List<Airline> airlines = new ArrayList<>();
}

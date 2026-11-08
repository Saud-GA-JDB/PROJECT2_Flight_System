package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "control_facilities")
public class ControlFacility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FacilityType facilityType = FacilityType.TOWER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private AirNavigationServiceProvider provider;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airport_id", nullable = false, unique = true)
    @JsonIgnore
    private Airport airport;

    public enum FacilityType {TOWER}
}

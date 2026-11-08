package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "atc_employees")
public class ATCEmployee extends Person {
    @Column(nullable = false, unique = true, length = 50)
    private String employeeNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ATCRole atcRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    @JsonIgnore
    private ControlFacility facility;

    public enum ATCRole {ADMIN, CONTROLLER}
}

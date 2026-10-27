package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "airport_operators")
public class AirportOperator {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contactEmail;

    @OneToMany(mappedBy = "operator", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Airport> airports = new ArrayList<>();

    @OneToMany(mappedBy = "operator", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<AirportEmployee> employees = new ArrayList<>();
}

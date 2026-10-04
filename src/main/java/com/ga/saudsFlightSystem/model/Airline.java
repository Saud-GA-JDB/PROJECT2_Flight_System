package com.ga.saudsFlightSystem.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "airline")
public class Airline {
    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    @Column(unique = true)
    private String airlineCode;

    @Column
    private String headquartersCountry;


    @OneToMany(mappedBy = "airline", fetch = FetchType.LAZY)
    private List<AirlineEmployee> airlineEmployeesList;

    @OneToMany(mappedBy = "airline", fetch = FetchType.LAZY)
    private List<Flight> flightsList;

    @OneToMany(mappedBy = "airline", fetch = FetchType.LAZY)
    private List<Airplane> airplanesList;


}

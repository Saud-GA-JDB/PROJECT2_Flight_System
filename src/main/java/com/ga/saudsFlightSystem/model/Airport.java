package com.ga.saudsFlightSystem.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "airports")
public class Airport {
    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String iataCode;

    @Column
    private String name;

    @Column
    private String city;

    @Column
    private String country;

    @Column
    private String timeZone;

    @OneToMany(mappedBy = "originAirport", fetch = FetchType.LAZY)
    private List<Flight> departingFlightsList;

    @OneToMany(mappedBy = "destinationAirport", fetch = FetchType.LAZY)
    private List<Flight> arrivingFlightsList;

}

package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "airplanes")
public class Airplane {
    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String registrationNumber;

    @Column
    private String model;

    @Column
    private int seatCapacity;

    @Column
    private Long maxMileage;

    @Column
    private String status;

    @ManyToOne
    @JoinColumn(name = "airline_id", nullable = false)
    @JsonIgnore
    private Airline airline;

    @OneToMany(mappedBy = "airplane", fetch = FetchType.LAZY)
    private List<Flight> flightsList;
}

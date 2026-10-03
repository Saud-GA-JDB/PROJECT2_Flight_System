package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
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
    @CreationTimestamp
    private LocalDateTime addedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.GROUNDED;

    @ManyToOne
    @JoinColumn(name = "airline_id", nullable = false)
    @JsonIgnore
    private Airline airline;

    @OneToMany(mappedBy = "airplane", fetch = FetchType.LAZY)
    private List<Flight> flightsList;
    public enum Status{ACTIVE, GROUNDED}

    @OneToMany(mappedBy = "airplane", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<AirplaneRequest> requests;
}

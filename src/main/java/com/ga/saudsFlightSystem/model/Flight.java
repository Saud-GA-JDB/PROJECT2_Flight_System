package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "flights")
public class Flight {
    @Column
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String flightNumber;

    @Column
    private LocalDateTime scheduledDeparture;

    @Column
    private LocalDateTime scheduledArrival;

    @Column
    private LocalDateTime actualDeparture;

    @Column
    private LocalDateTime actualArrival;

    @Column
    private String status;

    @ManyToOne
    @JoinColumn(name = "airline_id")
    @JsonIgnore
    private Airline airline;

    @ManyToOne
    @JoinColumn(name = "airplane_id")
    @JsonIgnore
    private Airplane airplane;

    @ManyToOne
    @JoinColumn(name = "origin_airport_id")
    private Airport originAirport;

    @ManyToOne
    @JoinColumn(name = "destination_airport_id")
    private Airport destinationAirport;

    @OneToMany(mappedBy = "flight", fetch = FetchType.LAZY)
    private List<Booking> bookingsList;


}

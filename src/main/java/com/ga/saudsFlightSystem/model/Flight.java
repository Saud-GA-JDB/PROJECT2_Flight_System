package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.Instant;
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
    private int firstClassSeatsCount;
    @Column
    private int standardSeatsCount;

    @Column
    private LocalDateTime scheduledDeparture;

    @Column
    private LocalDateTime scheduledArrival;

    @Column
    private LocalDateTime actualDeparture;

    @Column
    private LocalDateTime actualArrival;

    @Column
    @Enumerated(EnumType.STRING)
    private FlightStatus status;

    @Column
    private Long firstClassPrice;

    @Column
    private Long standardPrice;

    @Column(length = 3)
    @Enumerated(EnumType.STRING)
    private Currency currency = Currency.BHD;

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
    @JsonIgnore
    private Airport originAirport;

    @ManyToOne
    @JoinColumn(name = "destination_airport_id")
    @JsonIgnore
    private Airport destinationAirport;

    @OneToMany(mappedBy = "flight", fetch = FetchType.LAZY)
    private List<Booking> bookingsList;

    @Enumerated(EnumType.STRING)
    @Column
    private FlightOperation clearanceOperation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'NONE'")
    private ClearanceStatus clearanceStatus = ClearanceStatus.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clearance_facility_id")
    @JsonIgnore
    private ControlFacility clearanceFacility;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long clearanceCycle;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long clearanceRevision;

    @Column
    private Instant clearanceApprovedAt;

    @Column
    private Instant clearanceExpiresAt;

    public enum FlightStatus{PENDING_CREW, ACTIVE, IN_AIR, CLOSED, CANCELLED}

}

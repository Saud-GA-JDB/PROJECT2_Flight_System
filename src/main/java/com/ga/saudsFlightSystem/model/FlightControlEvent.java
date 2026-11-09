package com.ga.saudsFlightSystem.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "flight_control_events", uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "revision"}))
public class FlightControlEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false, updatable = false)
    private Flight flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false, updatable = false)
    private ControlFacility facility;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FlightOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EventType eventType;

    @Column(nullable = false, updatable = false)
    private long cycle;

    @Column(nullable = false, updatable = false)
    private long revision;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(length = 2000, updatable = false)
    private String reason;

    public enum EventType {REQUESTED, APPROVED, REJECTED, WITHDRAWN, REVOKED, TAKE_OFF, LANDED}
}

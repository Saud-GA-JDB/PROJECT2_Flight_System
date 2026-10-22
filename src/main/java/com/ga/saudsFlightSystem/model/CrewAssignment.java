package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "crew_assignments")
public class CrewAssignment {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "flight_id")
    @JsonIgnore
    private Flight flight;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    @JsonIgnore
    private AirlineEmployee airlineEmployee;

    @Column
    @Enumerated(EnumType.STRING)
    private Duty duty;

    @Column
    @CreationTimestamp
    private LocalDateTime assignedAt;
}

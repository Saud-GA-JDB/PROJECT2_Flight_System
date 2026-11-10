package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "airport_employees", uniqueConstraints =
        @UniqueConstraint(name = "uk_airport_employees_user_id", columnNames = "user_id"))
public class AirportEmployee extends Person{
    @Column(name = "user_id")
    @JsonIgnore
    private Long userId;

    public enum AirportRole {ADMIN, INSPECTOR, BAGGAGE_HANDLER}

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AirportRole airportRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airport_id", nullable = false)
    @JsonIgnore
    private Airport airport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    @JsonIgnore
    private AirportOperator operator;

}

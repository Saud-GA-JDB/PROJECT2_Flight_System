package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "airline_employees", uniqueConstraints =
        @UniqueConstraint(name = "uk_airline_employees_user_id", columnNames = "user_id"))
public class AirlineEmployee extends BaseEntity{
    @Column(name = "user_id")
    @JsonIgnore
    private Long userId;

    public enum AirlineRole{PILOT, FLIGHT_ATTENDANT, GATE_RECEPTION, ADMIN} //ill start with just pilot first
    @Column
    @Enumerated(EnumType.STRING)
    private AirlineRole airlineRole;

    @Column
    private LocalDate hireDate;

    @Column
    private Long salary;

    @ManyToOne
    @JoinColumn(name = "airline_id")
    @JsonIgnore
    private Airline airline;


}

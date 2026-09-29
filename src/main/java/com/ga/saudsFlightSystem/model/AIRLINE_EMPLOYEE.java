package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "airline_employees")
public class AIRLINE_EMPLOYEE extends Person{
    enum AirlineRole{PILOT, FLIGHT_ATTENDANT, GATE_RECEPTION} //ill start with just pilot first
    @Column
    private AirlineRole airlineRole;

    @Column
    private LocalDateTime hireDate;

    @Column
    private Long salary;

    @ManyToOne
    @JoinColumn(name = "airline_id")
    @JsonIgnore
    private Airline airline;
}

package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String emailAddress;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column
    private String securityQuestion;

    @Column
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String securityQuestionAnswer;

    @Column
    private Role role;

    @Column
    private boolean isActive;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", unique = true)
    private Customer customer;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "airline_employee_id", referencedColumnName = "id", unique = true)
    private AIRLINE_EMPLOYEE airlineEmployee;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "faa_admin_id", referencedColumnName = "id", unique = true)
    private FAAAdmin faaAdmin;

    public enum Role {CUSTOMER, AIRPORT_EMPLOYEE, AIRLINE_EMPLOYEE, FAA_ADMIN}
}

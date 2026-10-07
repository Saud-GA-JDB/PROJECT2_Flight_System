package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;
import java.time.LocalDate;

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

    @Column
    private Status status;

    @JsonIgnore
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedLoginAttempts;

    @JsonIgnore
    private LocalDate loginAttemptsDate;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", unique = true)
    private Customer customer;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "airline_employee_id", referencedColumnName = "id", unique = true)
    private AirlineEmployee airlineEmployee;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "faa_admin_id", referencedColumnName = "id", unique = true)
    private FAAAdmin faaAdmin;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Booking> bookingsList;

    public enum Role {CUSTOMER, AIRPORT_EMPLOYEE, AIRLINE_EMPLOYEE, FAA_ADMIN}

    public enum Status {SETUP_REQUIRED, ACTIVE, DEACTIVATED}
}

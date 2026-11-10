package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
    @Enumerated(EnumType.STRING)
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

    @JsonIgnore
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedPasswordResetAttempts;

    @JsonIgnore
    private LocalDate passwordResetAttemptsDate;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserProfile personalProfile;

    public void setPersonalProfile(UserProfile personalProfile) {
        this.personalProfile = personalProfile;
        if (personalProfile != null) {
            personalProfile.setUser(this);
        }
    }

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", unique = true)
    private Customer customer;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "airline_employee_id", referencedColumnName = "id", unique = true)
    private AirlineEmployee airlineEmployee;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "faa_admin_id", referencedColumnName = "id", unique = true)
    private FAAAdmin faaAdmin;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "maintenance_employee_id", referencedColumnName = "id", unique = true)
    private MaintenanceEmployee maintenanceEmployee;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "airport_employee_id", referencedColumnName = "id", unique = true)
    private AirportEmployee airportEmployee;

    // Save and update the ATC profile with its account, but do not cascade deletion.
    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "atc_employee_id", referencedColumnName = "id", unique = true)
    private ATCEmployee atcEmployee;

    public enum Role {CUSTOMER, AIRPORT_EMPLOYEE, AIRLINE_EMPLOYEE, FAA_ADMIN, MAINTENANCE_EMPLOYEE, ATC_EMPLOYEE}

    public enum Status {SETUP_REQUIRED, ACTIVE, DEACTIVATED}
}

package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@MappedSuperclass
public abstract class Person {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String fName;
    @Column
    private String lName;
    @Column(unique = true)
    private String emailAddress;

    @Column
    private String phoneNumberOpeningCode;
    @Column
    private String phoneNumber;
    @Column
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @Column
    private String securityQuestion;
    @Column
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String securityQuestionAnswer;
    @Lob
    @Column(name = "imagedata", length = 1000)
    private byte[] imageData;

    @Column(nullable = true)
    private String imageUrl;

    @Column
    @CreationTimestamp
    private LocalDateTime createdAt;
    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    @Column
    private  Role role;

    @Column
    private boolean isActive;

    public enum Role{CUSTOMER, AIRPORT_EMPLOYEE, AIRLINE_EMPLOYEE}

}

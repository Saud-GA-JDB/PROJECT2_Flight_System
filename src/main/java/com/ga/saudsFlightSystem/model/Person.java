package com.ga.saudsFlightSystem.model;

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

    @Column
    private String phoneNumberOpeningCode;
    @Column
    private String phoneNumber;
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

}

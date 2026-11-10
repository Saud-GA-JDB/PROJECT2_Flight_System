package com.ga.identity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "f_name")
    private String fName;

    @Column(name = "l_name")
    private String lName;

    @Column(nullable = false, unique = true)
    private String cpr;

    private String phoneNumberOpeningCode;

    private String phoneNumber;

    @Lob
    @Column(name = "imagedata", length = 1000)
    private byte[] imageData;

    private String imageUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public UserProfile(String fName, String lName, String cpr,
                       String phoneNumberOpeningCode, String phoneNumber) {
        this.fName = fName;
        this.lName = lName;
        this.cpr = cpr;
        this.phoneNumberOpeningCode = phoneNumberOpeningCode;
        this.phoneNumber = phoneNumber;
    }
}

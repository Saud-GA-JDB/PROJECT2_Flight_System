package com.ga.saudsFlightSystem.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.FAAAdmin;
import com.ga.saudsFlightSystem.model.User;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "airplane_requests")
public class AirplaneRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airplane_id", nullable = false)
    @JsonIgnore
    private Airplane airplane;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(length = 500)
    private String reviewReason;

    @Column
    @CreationTimestamp
    private LocalDateTime requestedAt;

    @Column
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    @JsonIgnore
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    @JsonIgnore
    private FAAAdmin reviewedBy;

    public enum ApprovalStatus{PENDING, ACCEPTED, DENIED}
}

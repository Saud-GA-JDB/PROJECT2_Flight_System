package com.ga.saudsFlightSystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "FAA_Admins")
public class FAAAdmin extends Person{
    @OneToMany(mappedBy = "reviewedBy", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<AirplaneRequest> reviewedRequests;
}

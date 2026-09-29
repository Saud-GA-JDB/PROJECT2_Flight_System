package com.ga.saudsFlightSystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "customers")
public class Customer extends Person {

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    List<Booking> bookingsList;
}

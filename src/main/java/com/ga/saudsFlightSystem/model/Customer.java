package com.ga.saudsFlightSystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;


@Data
@Entity
@Table(name = "customers")
public class Customer extends Person {

}

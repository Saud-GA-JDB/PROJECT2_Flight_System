package com.ga.saudsFlightSystem.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "FAA_Admins")
public class FAAAdmin extends Person{

}

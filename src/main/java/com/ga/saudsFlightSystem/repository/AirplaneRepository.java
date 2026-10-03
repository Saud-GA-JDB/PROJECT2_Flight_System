package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airplane;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirplaneRepository extends JpaRepository<Airplane, Long> {
    boolean existsByRegistrationNumber(String registrationNumber);
}

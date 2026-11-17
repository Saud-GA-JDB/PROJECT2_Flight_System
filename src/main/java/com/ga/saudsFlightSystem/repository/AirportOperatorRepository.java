package com.ga.saudsFlightSystem.repository;

import java.util.List;

import com.ga.saudsFlightSystem.model.AirportOperator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportOperatorRepository extends JpaRepository<AirportOperator, Long> {
    List<AirportOperator> findAllByOrderByNameAscIdAsc();
}

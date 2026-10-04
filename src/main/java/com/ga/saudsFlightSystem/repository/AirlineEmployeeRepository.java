package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AirlineEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AirlineEmployeeRepository extends JpaRepository<AirlineEmployee, Long> {
    boolean existsByCpr(String cpr);
}

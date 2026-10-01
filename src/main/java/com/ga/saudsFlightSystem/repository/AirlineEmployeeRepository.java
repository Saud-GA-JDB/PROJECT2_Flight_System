package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AIRLINE_EMPLOYEE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AirlineEmployeeRepository extends JpaRepository<AIRLINE_EMPLOYEE, Long> {
    boolean existsByCpr(String cpr);
}

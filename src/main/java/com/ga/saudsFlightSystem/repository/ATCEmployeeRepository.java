package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.ATCEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ATCEmployeeRepository extends JpaRepository<ATCEmployee, Long> {
    boolean existsByCpr(String cpr);

    boolean existsByEmployeeNumber(String employeeNumber);
}

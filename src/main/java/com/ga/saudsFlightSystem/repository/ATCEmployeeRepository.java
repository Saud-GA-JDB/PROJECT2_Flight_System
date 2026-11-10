package com.ga.saudsFlightSystem.repository;

import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.ga.saudsFlightSystem.model.ATCEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ATCEmployeeRepository extends JpaRepository<ATCEmployee, Long> {
    boolean existsByCpr(String cpr);

    boolean existsByEmployeeNumber(String employeeNumber);

    @Query("""
        SELECT e FROM ATCEmployee e
        LEFT JOIN FETCH e.facility f
        LEFT JOIN FETCH f.airport
        LEFT JOIN FETCH f.provider
        WHERE e.userId = :userId
        """)
    Optional<ATCEmployee> findByUserId(@Param("userId") Long userId);
}

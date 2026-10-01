package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByCpr(String cpr);
}

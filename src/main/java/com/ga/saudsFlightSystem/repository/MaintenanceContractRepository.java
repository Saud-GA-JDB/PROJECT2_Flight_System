package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.MaintenanceContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceContractRepository extends JpaRepository<MaintenanceContract, Long> {
    boolean existsByProviderIdAndAirlineId(Long providerId, Long airlineId);
}

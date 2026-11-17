package com.ga.maintenance.repository;

import java.util.List;

import com.ga.maintenance.model.MaintenanceContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceContractRepository extends JpaRepository<MaintenanceContract, Long> {
    boolean existsByProviderIdAndAirlineId(Long providerId, Long airlineId);

    List<MaintenanceContract> findByAirlineIdOrderByIdAsc(Long airlineId);

    List<MaintenanceContract> findByProviderIdOrderByIdAsc(Long providerId);
}

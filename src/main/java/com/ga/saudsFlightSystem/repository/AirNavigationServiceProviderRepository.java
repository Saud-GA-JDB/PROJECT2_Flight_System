package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.AirNavigationServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AirNavigationServiceProviderRepository extends JpaRepository<AirNavigationServiceProvider, Long> {
    @Query(value = "SELECT * FROM air_navigation_service_providers ORDER BY id LIMIT :size OFFSET :offset", nativeQuery = true)
    List<AirNavigationServiceProvider> findProviders(@Param("size") int size, @Param("offset") long offset);
}

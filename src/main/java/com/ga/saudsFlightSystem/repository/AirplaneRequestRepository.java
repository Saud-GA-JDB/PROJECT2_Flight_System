package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AirplaneRequestRepository extends JpaRepository<AirplaneRequest, Long> {
    List<AirplaneRequest> findByAirplane(Airplane airplane);
    List<AirplaneRequest> findByStatus(AirplaneRequest.ApprovalStatus status);
}

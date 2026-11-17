package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AirplaneRequestRepository extends JpaRepository<AirplaneRequest, Long> {
    Page<AirplaneRequest> findByAirplane_IdOrderByRequestedAtDescIdDesc(Long airplaneId, Pageable pageable);

    Optional<AirplaneRequest> findFirstByAirplane_IdOrderByRequestedAtDescIdDesc(Long airplaneId);

    boolean existsByAirplane_IdAndStatus(Long airplaneId, AirplaneRequest.ApprovalStatus status);

    List<AirplaneRequest> findByAirplane(Airplane airplane);
    List<AirplaneRequest> findByStatus(AirplaneRequest.ApprovalStatus status);
    @Query("SELECT r.airplane.id FROM AirplaneRequest r WHERE r.id = :requestId")
    Optional<Long> findAirplaneIdByRequestId(@Param("requestId") Long requestId);
}

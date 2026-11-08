package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class FlightReadinessService {
    private FlightRepository flightRepository;
    private CrewAssignmentRepository crewRepository;
    private FlightInspectionRepository inspectionRepository;
    private ControlFacilityRepository facilityRepository;

    @Transactional(readOnly = true)
    public ReadinessResponse evaluate(Flight flight, FlightOperation operation, Instant now) {
        List<ReadinessBlocker> blockers = new ArrayList<>();
        if (operation == null || (operation == FlightOperation.DEPARTURE && flight.getStatus() != Flight.FlightStatus.ACTIVE)
                || (operation == FlightOperation.ARRIVAL && flight.getStatus() != Flight.FlightStatus.IN_AIR))
            block(blockers, "WRONG_FLIGHT_PHASE", "REQUEST", "APPROVAL", "ACTION");

        if (operation == null)
            return response(flight, operation, now, blockers);

        Airport airport = operation == FlightOperation.DEPARTURE ? flight.getOriginAirport() : flight.getDestinationAirport();
        ControlFacility tower = airport == null ? null : facilityRepository.findByAirport_Id(airport.getId()).orElse(null);
        if (tower == null)
            block(blockers, "TOWER_NOT_CONFIGURED", "REQUEST", "APPROVAL", "ACTION");

        if (operation == FlightOperation.DEPARTURE) {
            if (flight.getActualDeparture() != null || flight.getActualArrival() != null)
                block(blockers, "ACTUAL_TIME_CONFLICT", "REQUEST", "APPROVAL", "ACTION");
            if (flight.getOriginAirport() == null || flight.getDestinationAirport() == null)
                block(blockers, "WRONG_FLIGHT_PHASE", "REQUEST", "APPROVAL", "ACTION");
            List<CrewAssignment> crew = crewRepository.findByFlightIdOrderByIdAsc(flight.getId());
            for (Duty duty : List.of(Duty.PILOT, Duty.SECONDARY_PILOT, Duty.FLIGHT_ATTENDANT)) {
                if (crew.stream().noneMatch(c -> c.getDuty() == duty && c.getAirlineEmployee() != null)) {
                    block(blockers, "CREW_INCOMPLETE", "REQUEST", "APPROVAL", "ACTION");
                    break;
                }
            }
            FlightInspection inspection = inspectionRepository.findFirstByFlight_IdOrderByIdDesc(flight.getId()).orElse(null);
            if (inspection == null || inspection.getStatus() != FlightInspection.InspectionStatus.PASSED)
                block(blockers, "INSPECTION_NOT_PASSED", "REQUEST", "APPROVAL", "ACTION");
            if (flight.getAirplane() == null || flight.getAirplane().getStatus() != Airplane.Status.ACTIVE)
                block(blockers, "AIRPLANE_NOT_ACTIVE", "REQUEST", "APPROVAL", "ACTION");
            if (flight.getAirplane() != null && flightRepository.hasOccupiedAirplane(flight.getId(), flight.getAirplane().getId()))
                block(blockers, "AIRPLANE_IN_USE", "REQUEST", "APPROVAL", "ACTION");
            List<Long> employeeIds = new ArrayList<>();
            for (CrewAssignment assignment : crew) {
                if (assignment.getAirlineEmployee() != null)
                    employeeIds.add(assignment.getAirlineEmployee().getId());
            }
            if (!employeeIds.isEmpty() && crewRepository.hasOccupiedCrew(flight.getId(), employeeIds))
                block(blockers, "CREW_IN_USE", "REQUEST", "APPROVAL", "ACTION");
        } else {
            LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Bahrain"));
            if (flight.getActualDeparture() == null || flight.getActualDeparture().isAfter(localNow) || flight.getActualArrival() != null)
                block(blockers, "ACTUAL_TIME_CONFLICT", "REQUEST", "APPROVAL", "ACTION");
        }

        String status = effectiveClearanceStatus(flight, now);
        boolean matchingOperation = flight.getClearanceOperation() == operation;
        boolean matchingTower = tower != null && flight.getClearanceFacility() != null
                && tower.getId().equals(flight.getClearanceFacility().getId());
        if (status.equals("PENDING")) {
            block(blockers, "REQUEST_ALREADY_PENDING", "REQUEST");
            if (!matchingOperation || !matchingTower)
                block(blockers, "CLEARANCE_OPERATION_MISMATCH", "APPROVAL", "ACTION");
        } else if (status.equals("APPROVED")) {
            block(blockers, "CLEARANCE_ALREADY_APPROVED", "REQUEST", "APPROVAL");
            if (!matchingOperation || !matchingTower)
                block(blockers, "CLEARANCE_OPERATION_MISMATCH", "ACTION");
            if (flight.getClearanceApprovedAt() == null || flight.getClearanceExpiresAt() == null
                    || flight.getClearanceApprovedAt().isAfter(now)
                    || !flight.getClearanceExpiresAt().isAfter(flight.getClearanceApprovedAt()))
                block(blockers, "ACTUAL_TIME_CONFLICT", "ACTION");
        } else if (status.equals("CONSUMED") && !(operation == FlightOperation.ARRIVAL
                && flight.getClearanceOperation() == FlightOperation.DEPARTURE && flight.getStatus() == Flight.FlightStatus.IN_AIR)) {
            block(blockers, "CLEARANCE_OPERATION_MISMATCH", "REQUEST", "APPROVAL", "ACTION");
        }
        if (status.equals("EXPIRED"))
            block(blockers, "CLEARANCE_EXPIRED", "ACTION");
        else if (!status.equals("APPROVED"))
            block(blockers, "CLEARANCE_REQUIRED", "ACTION");
        return response(flight, operation, now, blockers);
    }

    public String effectiveClearanceStatus(Flight flight, Instant now) {
        if (flight.getClearanceStatus() == ClearanceStatus.APPROVED && flight.getClearanceExpiresAt() != null
                && !now.isBefore(flight.getClearanceExpiresAt()))
            return "EXPIRED";
        return flight.getClearanceStatus().name();
    }

    private void block(List<ReadinessBlocker> blockers, String code, String... appliesTo) {
        if (blockers.stream().noneMatch(blocker -> blocker.getCode().equals(code)))
            blockers.add(new ReadinessBlocker(code, List.of(appliesTo)));
    }

    private ReadinessResponse response(Flight flight, FlightOperation operation, Instant now, List<ReadinessBlocker> blockers) {
        return new ReadinessResponse(flight.getId(), operation, now,
                blockers.stream().noneMatch(b -> b.getAppliesTo().contains("REQUEST")),
                blockers.stream().noneMatch(b -> b.getAppliesTo().contains("APPROVAL")),
                blockers.stream().noneMatch(b -> b.getAppliesTo().contains("ACTION")), flight.getClearanceRevision(), blockers);
    }
}

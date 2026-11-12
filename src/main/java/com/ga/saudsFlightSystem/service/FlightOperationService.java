package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.notification.NotificationPublisher;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.repository.ATCEmployeeRepository;
import com.ga.common.security.CurrentActorProvider;
import com.ga.common.security.CurrentActor;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.BusinessConflictException;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@AllArgsConstructor
public class FlightOperationService {
    private ATCEmployeeRepository atcEmployeeRepository;
    private CurrentActorProvider currentActorProvider;
    private FlightRepository flightRepository;
    private CrewAssignmentRepository crewRepository;
    private FlightInspectionRepository inspectionRepository;
    private FlightControlEventRepository eventRepository;
    private AccountLookup accountLookup;
    private FlightReadinessService readinessService;
    private AirplaneRepository airplaneRepository;
    private AirlineEmployeeRepository employeeRepository;
    private ControlFacilityRepository facilityRepository;
    private AuditLogService auditLogService;
    private NotificationPublisher notificationService;
    private static final Logger logger = Logger.getLogger(FlightOperationService.class.getName());

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationListResponse> getPilotFlights(Flight.FlightStatus status, LocalDate fromDate,
                                                                              LocalDate toDate, int page, int size) {
        CurrentActor actor = activeActor();
        AirlineEmployee pilot = requirePilot(actor);
        validatePagination(page, size);
        validateDates(fromDate, toDate);
        List<Flight> flights = flightRepository.findPilotOperations(pilot.getId(),
                status == null ? null : status.name(), startDate(fromDate), endDate(toDate), size, (long) page * size);
        long totalElements = flightRepository.countPilotOperations(pilot.getId(),
                status == null ? null : status.name(), startDate(fromDate), endDate(toDate));
        return ResponseEntity.ok(summaries(flights, page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationListResponse> getTowerFlights(FlightOperation operation, Flight.FlightStatus status,
                                                                              LocalDate fromDate, LocalDate toDate, int page, int size) {
        CurrentActor actor = activeActor();
        ATCEmployee towerEmployee = requireTower(actor);
        validatePagination(page, size);
        validateDates(fromDate, toDate);
        List<Flight> flights = flightRepository.findTowerOperations(towerEmployee.getFacility().getAirport().getId(),
                operation == null ? null : operation.name(), status == null ? null : status.name(),
                startDate(fromDate), endDate(toDate), size, (long) page * size);
        long totalElements = flightRepository.countTowerOperations(towerEmployee.getFacility().getAirport().getId(),
                operation == null ? null : operation.name(), status == null ? null : status.name(), startDate(fromDate), endDate(toDate));
        return ResponseEntity.ok(summaries(flights, page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationListResponse> getTowerRequests(FlightOperation operation, int page, int size) {
        CurrentActor actor = activeActor();
        ATCEmployee towerEmployee = requireTower(actor);
        validatePagination(page, size);
        Long facilityId = towerEmployee.getFacility().getId();
        List<Flight> flights = flightRepository.findPendingClearances(facilityId,
                operation == null ? null : operation.name(), size, (long) page * size);
        long totalElements = flightRepository.countPendingClearances(facilityId, operation == null ? null : operation.name());
        return ResponseEntity.ok(summaries(flights, page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationSummary> getPilotFlight(Long flightId) {
        return ResponseEntity.ok(summary(pilotFlight(flightId), Instant.now()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationSummary> getTowerFlight(Long flightId) {
        CurrentActor actor = activeActor();
        ATCEmployee towerEmployee = requireTower(actor);
        Flight flight = flight(flightId);
        checkTowerFlight(towerEmployee, flight);
        return ResponseEntity.ok(summary(flight, Instant.now()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationSummary> getAirlineFlight(Long flightId) {
        return ResponseEntity.ok(summary(airlineFlight(flightId), Instant.now()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ReadinessResponse> getPilotReadiness(Long flightId) {
        Flight flight = pilotFlight(flightId);
        FlightOperation operation = null;
        if (flight.getStatus() == Flight.FlightStatus.PENDING_CREW || flight.getStatus() == Flight.FlightStatus.ACTIVE)
            operation = FlightOperation.DEPARTURE;
        else if (flight.getStatus() == Flight.FlightStatus.IN_AIR)
            operation = FlightOperation.ARRIVAL;
        return ResponseEntity.ok(readinessService.evaluate(flight, operation, Instant.now()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ReadinessResponse> getTowerReadiness(Long flightId, FlightOperation operation) {
        CurrentActor actor = activeActor();
        ATCEmployee towerEmployee = requireTower(actor);
        if (operation == null)
            throw new InvalidInformationException("Choose DEPARTURE or ARRIVAL.");
        Flight flight = flight(flightId);
        Airport responsible = operation == FlightOperation.DEPARTURE ? flight.getOriginAirport() : flight.getDestinationAirport();
        if (responsible == null || !responsible.getId().equals(towerEmployee.getFacility().getAirport().getId()))
            throw new IllegalEndpoint("Only the responsible tower may view readiness for this operation.");
        return ResponseEntity.ok(readinessService.evaluate(flight, operation, Instant.now()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ControlEventListResponse> getPilotEvents(Long flightId, FlightOperation operation, int page, int size) {
        pilotFlight(flightId);
        return ResponseEntity.ok(events(flightId, null, operation, page, size));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ControlEventListResponse> getTowerEvents(Long flightId, FlightOperation operation, int page, int size) {
        CurrentActor actor = activeActor();
        ATCEmployee towerEmployee = requireTower(actor);
        checkTowerFlight(towerEmployee, flight(flightId));
        return ResponseEntity.ok(events(flightId, towerEmployee.getFacility().getId(), operation, page, size));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ControlEventListResponse> getAirlineEvents(Long flightId, FlightOperation operation, int page, int size) {
        airlineFlight(flightId);
        return ResponseEntity.ok(events(flightId, null, operation, page, size));
    }

    @Transactional
    public ResponseEntity<OperationResponse> requestClearance(Long flightId, FlightOperation operation, Long expectedRevision) {
        validateCommand(flightId, operation, expectedRevision);
        Flight flight = lockOperation(flightId, operation == FlightOperation.DEPARTURE);
        CurrentActor actor = commandPilot(flight);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, operation, expectedRevision, now);
        checkReadiness(flight, operation, now, "REQUEST");
        startCycle(flight, operation, tower);
        flight.setClearanceStatus(ClearanceStatus.PENDING);
        return ResponseEntity.status(HttpStatus.CREATED).body(finishCommand(flight, actor, FlightControlEvent.EventType.REQUESTED, null, now));
    }

    @Transactional
    public ResponseEntity<OperationResponse> withdrawClearance(Long flightId, FlightOperation operation, Long expectedRevision) {
        validateCommand(flightId, operation, expectedRevision);
        Flight flight = lockOperation(flightId, false);
        CurrentActor actor = commandPilot(flight);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, operation, expectedRevision, now);
        requireCurrentClearance(flight, operation, tower, ClearanceStatus.PENDING);
        flight.setClearanceStatus(ClearanceStatus.WITHDRAWN);
        return ResponseEntity.ok(finishCommand(flight, actor, FlightControlEvent.EventType.WITHDRAWN, null, now));
    }

    @Transactional
    public ResponseEntity<OperationResponse> decideClearance(Long flightId, FlightOperation operation, Long expectedRevision,
                                                            ClearanceStatus decision, String reason) {
        validateCommand(flightId, operation, expectedRevision);
        if (decision != ClearanceStatus.APPROVED && decision != ClearanceStatus.REJECTED)
            throw new InvalidInformationException("Choose APPROVED or REJECTED.");
        reason = validateReason(reason, decision == ClearanceStatus.REJECTED);
        Flight flight = lockOperation(flightId, operation == FlightOperation.DEPARTURE && decision == ClearanceStatus.APPROVED);
        CurrentActor actor = commandTower(flight, operation);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, operation, expectedRevision, now);
        requireCurrentClearance(flight, operation, tower, ClearanceStatus.PENDING);
        if (decision == ClearanceStatus.APPROVED) {
            checkReadiness(flight, operation, now, "APPROVAL");
            approveClearance(flight, now);
        } else {
            flight.setClearanceStatus(ClearanceStatus.REJECTED);
        }
        return ResponseEntity.ok(finishCommand(flight, actor, decision == ClearanceStatus.APPROVED
                ? FlightControlEvent.EventType.APPROVED : FlightControlEvent.EventType.REJECTED, reason, now));
    }

    @Transactional
    public ResponseEntity<OperationResponse> directClearance(Long flightId, FlightOperation operation, Long expectedRevision, String reason) {
        validateCommand(flightId, operation, expectedRevision);
        reason = validateReason(reason, false);
        Flight flight = lockOperation(flightId, operation == FlightOperation.DEPARTURE);
        CurrentActor actor = commandTower(flight, operation);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, operation, expectedRevision, now);
        checkReadiness(flight, operation, now, "APPROVAL");
        if (flight.getClearanceStatus() == ClearanceStatus.PENDING)
            requireCurrentClearance(flight, operation, tower, ClearanceStatus.PENDING);
        else
            startCycle(flight, operation, tower);
        approveClearance(flight, now);
        return ResponseEntity.ok(finishCommand(flight, actor, FlightControlEvent.EventType.APPROVED, reason, now));
    }

    @Transactional
    public ResponseEntity<OperationResponse> revokeClearance(Long flightId, FlightOperation operation, Long expectedRevision, String reason) {
        validateCommand(flightId, operation, expectedRevision);
        reason = validateReason(reason, true);
        Flight flight = lockOperation(flightId, false);
        CurrentActor actor = commandTower(flight, operation);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, operation, expectedRevision, now);
        requireCurrentClearance(flight, operation, tower, ClearanceStatus.APPROVED);
        if (flight.getClearanceApprovedAt() == null || flight.getClearanceApprovedAt().isAfter(now)
                || flight.getClearanceExpiresAt() == null || !now.isBefore(flight.getClearanceExpiresAt()))
            throw new BusinessConflictException("Clearance is expired or has invalid approval times. Refresh the flight.");
        flight.setClearanceStatus(ClearanceStatus.REVOKED);
        return ResponseEntity.ok(finishCommand(flight, actor, FlightControlEvent.EventType.REVOKED, reason, now));
    }

    @Transactional
    public ResponseEntity<OperationResponse> takeOff(Long flightId, Long expectedRevision) {
        validateCommand(flightId, FlightOperation.DEPARTURE, expectedRevision);
        Flight flight = lockOperation(flightId, true);
        CurrentActor actor = commandPilot(flight);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, FlightOperation.DEPARTURE, expectedRevision, now);
        requireCurrentClearance(flight, FlightOperation.DEPARTURE, tower, ClearanceStatus.APPROVED);
        checkReadiness(flight, FlightOperation.DEPARTURE, now, "ACTION");
        flight.setActualDeparture(LocalDateTime.ofInstant(now, ZoneId.of("Asia/Bahrain")));
        flight.setStatus(Flight.FlightStatus.IN_AIR);
        flight.setClearanceStatus(ClearanceStatus.CONSUMED);
        return ResponseEntity.ok(finishCommand(flight, actor, FlightControlEvent.EventType.TAKE_OFF, null, now));
    }

    @Transactional
    public ResponseEntity<OperationResponse> land(Long flightId, Long expectedRevision) {
        validateCommand(flightId, FlightOperation.ARRIVAL, expectedRevision);
        Flight flight = lockOperation(flightId, true);
        CurrentActor actor = commandPilot(flight);
        Instant now = Instant.now();
        ControlFacility tower = checkCommand(flight, FlightOperation.ARRIVAL, expectedRevision, now);
        requireCurrentClearance(flight, FlightOperation.ARRIVAL, tower, ClearanceStatus.APPROVED);
        checkReadiness(flight, FlightOperation.ARRIVAL, now, "ACTION");
        flight.setActualArrival(LocalDateTime.ofInstant(now, ZoneId.of("Asia/Bahrain")));
        flight.setStatus(Flight.FlightStatus.CLOSED);
        flight.setClearanceStatus(ClearanceStatus.CONSUMED);
        return ResponseEntity.ok(finishCommand(flight, actor, FlightControlEvent.EventType.LANDED, null, now));
    }

    private void validateCommand(Long flightId, FlightOperation operation, Long expectedRevision) {
        if (flightId == null || flightId <= 0)
            throw new InvalidInformationException("Flight ID must be positive.");
        if (operation == null)
            throw new InvalidInformationException("Choose DEPARTURE or ARRIVAL.");
        if (expectedRevision == null || expectedRevision < 0)
            throw new InvalidInformationException("expectedRevision is required and must be nonnegative.");
    }

    private String validateReason(String reason, boolean required) {
        reason = reason == null ? null : reason.trim();
        if (required && (reason == null || reason.isEmpty()))
            throw new InvalidInformationException("A reason is required.");
        if (reason != null && reason.length() > 2000)
            throw new InvalidInformationException("Reason must not be more than 2000 characters.");
        return reason == null || reason.isEmpty() ? null : reason;
    }

    private Flight lockOperation(Long flightId, boolean resources) {
        Long airplaneId = null;
        if (resources) {
            airplaneId = flightRepository.findAirplaneIdForOperation(flightId);
            if (airplaneId != null)
                airplaneRepository.findByIdForUpdate(airplaneId)
                        .orElseThrow(() -> new BusinessConflictException("The airplane changed. Refresh and retry."));
        }
        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a flight with this ID."));
        if (resources) {
            if (airplaneId == null || flight.getAirplane() == null || !airplaneId.equals(flight.getAirplane().getId()))
                throw new BusinessConflictException("The flight airplane is missing or changed. Refresh and retry.");
            for (Long employeeId : crewRepository.findEmployeeIdsForOperation(flightId))
                employeeRepository.findByIdForAssignment(employeeId)
                        .orElseThrow(() -> new BusinessConflictException("Assigned crew changed. Refresh and retry."));
        }
        return flight;
    }

    private CurrentActor commandPilot(Flight flight) {
        CurrentActor actor = activeActor();
        AirlineEmployee pilot = requirePilot(actor);
        if (!crewRepository.isAssignedPilot(flight.getId(), pilot.getId()))
            throw new IllegalEndpoint("You are not currently assigned as a pilot on this flight.");
        return actor;
    }

    private CurrentActor commandTower(Flight flight, FlightOperation operation) {
        CurrentActor actor = activeActor();
        ATCEmployee towerEmployee = requireTower(actor);
        Airport airport = operation == FlightOperation.DEPARTURE ? flight.getOriginAirport() : flight.getDestinationAirport();
        if (airport == null || !airport.getId().equals(towerEmployee.getFacility().getAirport().getId()))
            throw new IllegalEndpoint("Only the responsible tower may perform this operation.");
        return actor;
    }

    private ControlFacility checkCommand(Flight flight, FlightOperation operation, Long expectedRevision, Instant now) {
        if (flight.getClearanceRevision() != expectedRevision)
            throw new BusinessConflictException("Clearance revision changed. Refresh and retry.");
        if (operation == FlightOperation.DEPARTURE) {
            if (flight.getStatus() != Flight.FlightStatus.ACTIVE || flight.getActualDeparture() != null || flight.getActualArrival() != null)
                throw new BusinessConflictException("Departure requires an active flight that has not taken off.");
        } else {
            LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Bahrain"));
            if (flight.getStatus() != Flight.FlightStatus.IN_AIR || flight.getActualDeparture() == null
                    || flight.getActualDeparture().isAfter(localNow) || flight.getActualArrival() != null)
                throw new BusinessConflictException("Arrival requires an airborne flight with consistent actual times.");
        }
        Airport airport = operation == FlightOperation.DEPARTURE ? flight.getOriginAirport() : flight.getDestinationAirport();
        if (airport == null)
            throw new BusinessConflictException("The responsible airport is missing.");
        return facilityRepository.findByAirport_Id(airport.getId())
                .orElseThrow(() -> new BusinessConflictException("The responsible tower is not configured."));
    }

    private void checkReadiness(Flight flight, FlightOperation operation, Instant now, String stage) {
        for (ReadinessBlocker blocker : readinessService.evaluate(flight, operation, now).getBlockers()) {
            if (blocker.getAppliesTo().contains(stage))
                throw new BusinessConflictException("Flight operation blocked: " + blocker.getCode());
        }
    }

    private void requireCurrentClearance(Flight flight, FlightOperation operation, ControlFacility tower, ClearanceStatus status) {
        if (flight.getClearanceStatus() != status || flight.getClearanceOperation() != operation
                || flight.getClearanceFacility() == null || !tower.getId().equals(flight.getClearanceFacility().getId()))
            throw new BusinessConflictException("Clearance state or operation changed. Refresh and retry.");
    }

    private void startCycle(Flight flight, FlightOperation operation, ControlFacility tower) {
        flight.setClearanceCycle(flight.getClearanceCycle() + 1);
        flight.setClearanceOperation(operation);
        flight.setClearanceFacility(tower);
        flight.setClearanceApprovedAt(null);
        flight.setClearanceExpiresAt(null);
    }

    private void approveClearance(Flight flight, Instant now) {
        flight.setClearanceStatus(ClearanceStatus.APPROVED);
        flight.setClearanceApprovedAt(now);
        flight.setClearanceExpiresAt(now.plusSeconds(1200));
    }

    private OperationResponse finishCommand(Flight flight, CurrentActor actor, FlightControlEvent.EventType type, String reason, Instant now) {
        flight.setClearanceRevision(flight.getClearanceRevision() + 1);
        flightRepository.save(flight);
        FlightControlEvent event = new FlightControlEvent();
        event.setFlight(flight);
        event.setFacility(flight.getClearanceFacility());
        event.setActorUserId(actor.userId());
        event.setOperation(flight.getClearanceOperation());
        event.setEventType(type);
        event.setCycle(flight.getClearanceCycle());
        event.setRevision(flight.getClearanceRevision());
        event.setOccurredAt(now);
        event.setReason(reason);
        eventRepository.save(event);
        String description = "Flight " + flight.getId() + " clearance " + type + " cycle " + event.getCycle()
                + " revision " + event.getRevision() + " by user " + actor.userId();
        AuditLog.Action action;
        if (type == FlightControlEvent.EventType.TAKE_OFF)
            action = AuditLog.Action.FLIGHT_TAKE_OFF;
        else if (type == FlightControlEvent.EventType.LANDED)
            action = AuditLog.Action.FLIGHT_LANDED;
        else
            action = AuditLog.Action.valueOf("FLIGHT_CLEARANCE_" + type.name());
        auditLogService.addAuditLogForActor(actor, action,
                AuditLog.EntityType.FLIGHT_CONTROL_EVENT, event.getId(), description);
        logger.info(description);
        String eventName = "Clearance_" + type.name().charAt(0) + type.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        if (type == FlightControlEvent.EventType.TAKE_OFF)
            eventName = "Flight_Departed";
        else if (type == FlightControlEvent.EventType.LANDED)
            eventName = "Flight_Landed";
        ClearanceNotificationResponse payload = new ClearanceNotificationResponse(event.getId(), flight.getId(),
                flight.getClearanceOperation(), event.getCycle(), event.getRevision(), flight.getClearanceStatus(), now,
                type == FlightControlEvent.EventType.APPROVED ? flight.getClearanceExpiresAt() : null);
        Set<Long> recipients = new LinkedHashSet<>(accountLookup.findActiveUserIds(
                employeeRepository.findFlightRecipientUserIds(flight.getId(), flight.getAirline().getId()), "AIRLINE_EMPLOYEE"));

        List<Long> towerUserIds;
        if (type == FlightControlEvent.EventType.TAKE_OFF || type == FlightControlEvent.EventType.LANDED) {
            towerUserIds = atcEmployeeRepository.findFlightActionRecipientUserIds(flight.getId());
        } else {
            towerUserIds = atcEmployeeRepository.findClearanceRecipientUserIds(flight.getClearanceFacility().getId());
        }
        recipients.addAll(accountLookup.findActiveUserIds(towerUserIds, "ATC_EMPLOYEE"));

        for (Long recipientId : recipients) {
            notificationService.sendToUser(recipientId, eventName, payload);
        }
        ClearanceResponse clearance = new ClearanceResponse(flight.getClearanceOperation(), flight.getClearanceStatus().name(),
                flight.getClearanceFacility().getId(), flight.getClearanceCycle(), flight.getClearanceRevision(),
                flight.getClearanceApprovedAt(), flight.getClearanceExpiresAt());
        return new OperationResponse(flight.getId(), flight.getFlightNumber(), flight.getStatus(), flight.getActualDeparture(),
                flight.getActualArrival(), clearance, event.getId());
    }

    // helper methods
    private CurrentActor activeActor() {
        CurrentActor actor = currentActorProvider.getFreshCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()))
            throw new IllegalEndpoint("An active account is required.");
        return actor;
    }

    private AirlineEmployee requirePilot(CurrentActor actor) {
        AirlineEmployee employee = employeeRepository.findByUserId(actor.userId()).orElse(null);
        if (!"AIRLINE_EMPLOYEE".equals(actor.role()) || employee == null
                || employee.getAirlineRole() != AirlineEmployee.AirlineRole.PILOT)
            throw new IllegalEndpoint("Only assigned pilots may access these flight operations.");
        return employee;
    }

    private ATCEmployee requireTower(CurrentActor actor) {
        ATCEmployee employee = atcEmployeeRepository.findByUserId(actor.userId()).orElse(null);
        if (!"ATC_EMPLOYEE".equals(actor.role()) || employee == null || employee.getFacility() == null
                || employee.getFacility().getAirport() == null
                || (employee.getAtcRole() != ATCEmployee.ATCRole.ADMIN && employee.getAtcRole() != ATCEmployee.ATCRole.CONTROLLER))
            throw new IllegalEndpoint("Only tower staff may access these flight operations.");
        return employee;
    }

    private Flight pilotFlight(Long flightId) {
        CurrentActor actor = activeActor();
        AirlineEmployee pilot = requirePilot(actor);
        Flight flight = flight(flightId);
        if (!crewRepository.isAssignedPilot(flightId, pilot.getId()))
            throw new IllegalEndpoint("You are not currently assigned as a pilot on this flight.");
        return flight;
    }

    private Flight airlineFlight(Long flightId) {
        CurrentActor actor = activeActor();
        AirlineEmployee employee = employeeRepository.findByUserId(actor.userId()).orElse(null);
        if (!"AIRLINE_EMPLOYEE".equals(actor.role()) || employee == null
                || employee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN || employee.getAirline() == null)
            throw new IllegalEndpoint("Only airline admins may access this endpoint.");
        Flight flight = flight(flightId);
        if (flight.getAirline() == null || !flight.getAirline().getId().equals(employee.getAirline().getId()))
            throw new IllegalEndpoint("You may only view your airline's operations.");
        return flight;
    }

    private void checkTowerFlight(ATCEmployee towerEmployee, Flight flight) {
        Long airportId = towerEmployee.getFacility().getAirport().getId();
        if ((flight.getOriginAirport() == null || !airportId.equals(flight.getOriginAirport().getId()))
                && (flight.getDestinationAirport() == null || !airportId.equals(flight.getDestinationAirport().getId())))
            throw new IllegalEndpoint("This flight does not use your tower's airport.");
    }

    private Flight flight(Long id) {
        if (id == null || id <= 0)
            throw new InvalidInformationException("Flight ID must be positive.");
        return flightRepository.findById(id).orElseThrow(() -> new InformationNotFoundException("Couldn't find a flight with this ID."));
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new InvalidInformationException("Page must be nonnegative and size must be between 1 and 100.");
    }

    private long totalPages(long totalElements, int size) {
        long totalPages = totalElements / size;
        if (totalElements % size != 0)
            totalPages++;
        return totalPages;
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to))
            throw new InvalidInformationException("fromDate must not be after toDate.");
    }

    private LocalDateTime startDate(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime endDate(LocalDate date) {
        try {
            return date == null ? null : date.plusDays(1).atStartOfDay();
        } catch (DateTimeException ex) {
            throw new InvalidInformationException("toDate is outside the supported range.");
        }
    }

    private FlightOperationListResponse summaries(List<Flight> flights, int page, int size, long totalElements) {
        Instant now = Instant.now();
        List<FlightOperationSummary> content = new ArrayList<>();
        for (Flight flight : flights) content.add(summary(flight, now));
        return new FlightOperationListResponse(content, page, size, totalElements, totalPages(totalElements, size));
    }

    private FlightOperationSummary summary(Flight flight, Instant now) {
        List<OperationCrewResponse> crew = new ArrayList<>();
        for (CrewAssignment assignment : crewRepository.findByFlightIdOrderByIdAsc(flight.getId()))
            crew.add(new OperationCrewResponse(assignment.getAirlineEmployee() == null ? null : assignment.getAirlineEmployee().getId(), assignment.getDuty()));
        FlightInspection inspection = inspectionRepository.findFirstByFlight_IdOrderByIdDesc(flight.getId()).orElse(null);
        ClearanceResponse clearance = new ClearanceResponse(flight.getClearanceOperation(), readinessService.effectiveClearanceStatus(flight, now),
                flight.getClearanceFacility() == null ? null : flight.getClearanceFacility().getId(), flight.getClearanceCycle(),
                flight.getClearanceRevision(), flight.getClearanceApprovedAt(), flight.getClearanceExpiresAt());
        return new FlightOperationSummary(flight.getId(), flight.getFlightNumber(),
                flight.getAirline() == null ? null : flight.getAirline().getId(), flight.getAirplane() == null ? null : flight.getAirplane().getId(),
                flight.getOriginAirport() == null ? null : flight.getOriginAirport().getId(),
                flight.getDestinationAirport() == null ? null : flight.getDestinationAirport().getId(),
                flight.getScheduledDeparture(), flight.getScheduledArrival(), flight.getActualDeparture(), flight.getActualArrival(),
                flight.getStatus(), clearance, crew, inspection == null ? null : new OperationInspectionResponse(inspection.getId(), inspection.getStatus()));
    }

    private ControlEventListResponse events(Long flightId, Long facilityId, FlightOperation operation, int page, int size) {
        validatePagination(page, size);
        List<FlightControlEvent> events = eventRepository.findHistory(flightId, facilityId, operation == null ? null : operation.name(), size, (long) page * size);
        long totalElements = eventRepository.countHistory(flightId, facilityId, operation == null ? null : operation.name());
        List<ControlEventResponse> content = new ArrayList<>();
        for (FlightControlEvent event : events)
            content.add(new ControlEventResponse(event.getId(), flightId, event.getFacility().getId(), event.getActorUserId(),
                    event.getOperation(), event.getEventType(), event.getCycle(), event.getRevision(), event.getOccurredAt(), event.getReason()));
        return new ControlEventListResponse(content, page, size, totalElements, totalPages(totalElements, size));
    }
}

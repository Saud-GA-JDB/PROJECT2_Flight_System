package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class FlightOperationService {
    private FlightRepository flightRepository;
    private CrewAssignmentRepository crewRepository;
    private FlightInspectionRepository inspectionRepository;
    private FlightControlEventRepository eventRepository;
    private UserRepository userRepository;
    private FlightReadinessService readinessService;

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationListResponse> getPilotFlights(Flight.FlightStatus status, LocalDate fromDate,
                                                                              LocalDate toDate, int page, int size) {
        User user = requirePilot();
        validatePagination(page, size);
        validateDates(fromDate, toDate);
        List<Flight> flights = flightRepository.findPilotOperations(user.getAirlineEmployee().getId(),
                status == null ? null : status.name(), startDate(fromDate), endDate(toDate), size, (long) page * size);
        long totalElements = flightRepository.countPilotOperations(user.getAirlineEmployee().getId(),
                status == null ? null : status.name(), startDate(fromDate), endDate(toDate));
        return ResponseEntity.ok(summaries(flights, page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationListResponse> getTowerFlights(FlightOperation operation, Flight.FlightStatus status,
                                                                              LocalDate fromDate, LocalDate toDate, int page, int size) {
        User user = requireTower();
        validatePagination(page, size);
        validateDates(fromDate, toDate);
        List<Flight> flights = flightRepository.findTowerOperations(user.getAtcEmployee().getFacility().getAirport().getId(),
                operation == null ? null : operation.name(), status == null ? null : status.name(),
                startDate(fromDate), endDate(toDate), size, (long) page * size);
        long totalElements = flightRepository.countTowerOperations(user.getAtcEmployee().getFacility().getAirport().getId(),
                operation == null ? null : operation.name(), status == null ? null : status.name(), startDate(fromDate), endDate(toDate));
        return ResponseEntity.ok(summaries(flights, page, size, totalElements));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<FlightOperationListResponse> getTowerRequests(FlightOperation operation, int page, int size) {
        User user = requireTower();
        validatePagination(page, size);
        Long facilityId = user.getAtcEmployee().getFacility().getId();
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
        User user = requireTower();
        Flight flight = flight(flightId);
        checkTowerFlight(user, flight);
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
        User user = requireTower();
        if (operation == null)
            throw new InvalidInformationException("Choose DEPARTURE or ARRIVAL.");
        Flight flight = flight(flightId);
        Airport responsible = operation == FlightOperation.DEPARTURE ? flight.getOriginAirport() : flight.getDestinationAirport();
        if (responsible == null || !responsible.getId().equals(user.getAtcEmployee().getFacility().getAirport().getId()))
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
        User user = requireTower();
        checkTowerFlight(user, flight(flightId));
        return ResponseEntity.ok(events(flightId, user.getAtcEmployee().getFacility().getId(), operation, page, size));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ControlEventListResponse> getAirlineEvents(Long flightId, FlightOperation operation, int page, int size) {
        airlineFlight(flightId);
        return ResponseEntity.ok(events(flightId, null, operation, page, size));
    }

    // helper methods
    private User activeUser() {
        User principal = UserService.getCurrentLoggedInUser();
        User user = principal == null ? null : userRepository.findById(principal.getId()).orElse(null);
        if (user == null || !user.isActive() || user.getStatus() != User.Status.ACTIVE)
            throw new IllegalEndpoint("An active account is required.");
        return user;
    }

    private User requirePilot() {
        User user = activeUser();
        if (user.getRole() != User.Role.AIRLINE_EMPLOYEE || user.getAirlineEmployee() == null
                || user.getAirlineEmployee().getAirlineRole() != AirlineEmployee.AirlineRole.PILOT)
            throw new IllegalEndpoint("Only assigned pilots may access these flight operations.");
        return user;
    }

    private User requireTower() {
        User user = activeUser();
        ATCEmployee employee = user.getAtcEmployee();
        if (user.getRole() != User.Role.ATC_EMPLOYEE || employee == null || employee.getFacility() == null
                || employee.getFacility().getAirport() == null
                || (employee.getAtcRole() != ATCEmployee.ATCRole.ADMIN && employee.getAtcRole() != ATCEmployee.ATCRole.CONTROLLER))
            throw new IllegalEndpoint("Only tower staff may access these flight operations.");
        return user;
    }

    private Flight pilotFlight(Long flightId) {
        User user = requirePilot();
        Flight flight = flight(flightId);
        if (!crewRepository.isAssignedPilot(flightId, user.getAirlineEmployee().getId()))
            throw new IllegalEndpoint("You are not currently assigned as a pilot on this flight.");
        return flight;
    }

    private Flight airlineFlight(Long flightId) {
        User user = activeUser();
        AirlineEmployee employee = user.getAirlineEmployee();
        if (user.getRole() != User.Role.AIRLINE_EMPLOYEE || employee == null
                || employee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN || employee.getAirline() == null)
            throw new IllegalEndpoint("Only airline admins may access this endpoint.");
        Flight flight = flight(flightId);
        if (flight.getAirline() == null || !flight.getAirline().getId().equals(employee.getAirline().getId()))
            throw new IllegalEndpoint("You may only view your airline's operations.");
        return flight;
    }

    private void checkTowerFlight(User user, Flight flight) {
        Long airportId = user.getAtcEmployee().getFacility().getAirport().getId();
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
            content.add(new ControlEventResponse(event.getId(), flightId, event.getFacility().getId(), event.getActorUser().getId(),
                    event.getOperation(), event.getEventType(), event.getCycle(), event.getRevision(), event.getOccurredAt(), event.getReason()));
        return new ControlEventListResponse(content, page, size, totalElements, totalPages(totalElements, size));
    }
}

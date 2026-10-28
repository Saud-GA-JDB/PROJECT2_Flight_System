package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.response.FlightInspectionResponse;
import com.ga.saudsFlightSystem.repository.AirportEmployeeRepository;
import com.ga.saudsFlightSystem.repository.FlightInspectionRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class FlightInspectionService {

    private FlightRepository flightRepository;
    private FlightInspectionRepository flightInspectionRepository;
    private AirportEmployeeRepository airportEmployeeRepository;
    private AuditLogService auditLogService;
    private static final Logger logger = Logger.getLogger(FlightInspectionService.class.getName());

    @Transactional
    public ResponseEntity<?> assignInspector(Long flightId, Long inspectorId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airportEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirportEmployee admin = user.getAirportEmployee();

        if (admin == null || admin.getAirportRole() != AirportEmployee.AirportRole.ADMIN
                || admin.getAirport() == null || admin.getOperator() == null)
            throw new IllegalEndpoint("Only airport admins can assign inspectors.");

        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (flight.getStatus() != Flight.FlightStatus.PENDING_CREW && flight.getStatus() != Flight.FlightStatus.ACTIVE)
            throw new InvalidInformationException("Inspections can only be assigned before departure.");

        Airport airport = flight.getOriginAirport();

        if (airport == null || !admin.getAirport().getId().equals(airport.getId()) || airport.getOperator() == null
                || !admin.getOperator().getId().equals(airport.getOperator().getId()))
            throw new IllegalEndpoint("You can only assign inspections at your own airport.");

        FlightInspection inspection = flightInspectionRepository.findFirstByFlight_IdOrderByIdDesc(flightId).orElse(null);

        if (inspection != null) {
            if (inspection.getStatus() != FlightInspection.InspectionStatus.PENDING)
                throw new InvalidInformationException("Can't assign to an inspection that its state is not PENDING.");
            if (inspection.getInspector() != null)
                throw new InvalidInformationException("An inspector is already assigned.");
        }

        AirportEmployee inspector = airportEmployeeRepository.findById(inspectorId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an employee with this ID."));

        if (inspector.getAirportRole() != AirportEmployee.AirportRole.INSPECTOR
                || inspector.getAirport() == null || !inspector.getAirport().getId().equals(airport.getId())
                || inspector.getOperator() == null || !inspector.getOperator().getId().equals(airport.getOperator().getId()))
            throw new InvalidInformationException("Choose an inspector belonging to this airport and operator.");

        if (inspection == null) {
            inspection = new FlightInspection();
            inspection.setFlight(flight);
            inspection.setAirport(airport);
        }
        inspection.setInspector(inspector);

        flightInspectionRepository.save(inspection);

        String description = "Airport admin assigned inspector " + inspectorId
                + " to inspection " + inspection.getId() + " for flight " + flightId;

        auditLogService.addAuditLog(user, AuditLog.Action.FLIGHT_INSPECTOR_ASSIGNED,
                AuditLog.EntityType.FLIGHT_INSPECTION, inspection.getId(), description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.ok("Inspector assigned successfully. Inspection ID: " + inspection.getId());
    }

    @Transactional
    public ResponseEntity<?> completeInspection(Long flightId, Long inspectionId, FlightInspection.InspectionStatus result, String reason) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airportEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirportEmployee inspector = user.getAirportEmployee();

        if (inspector == null || inspector.getAirportRole() != AirportEmployee.AirportRole.INSPECTOR
                || inspector.getAirport() == null || inspector.getOperator() == null)
            throw new IllegalEndpoint("Only inspectors can complete inspections.");

        if (result != FlightInspection.InspectionStatus.PASSED && result != FlightInspection.InspectionStatus.FAILED)
            throw new InvalidInformationException("Choose PASSED or FAILED.");

        if (reason != null && reason.length() > 2000)
            throw new InvalidInformationException("Reason must not be more than 2000 characters.");

        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (flight.getStatus() != Flight.FlightStatus.PENDING_CREW && flight.getStatus() != Flight.FlightStatus.ACTIVE)
            throw new InvalidInformationException("Inspections can only be completed before departure.");

        FlightInspection inspection = flightInspectionRepository.findFirstByFlight_IdOrderByIdDesc(flightId)
                .orElseThrow(() -> new InformationNotFoundException("No inspection exists for this flight."));

        if (!inspection.getId().equals(inspectionId))
            throw new InvalidInformationException("Only the most recent inspection attempt can be done.");

        if (inspection.getStatus() != FlightInspection.InspectionStatus.PENDING)
            throw new InvalidInformationException("This inspection has already been completed.");

        if (inspection.getInspector() == null || !inspection.getInspector().getId().equals(inspector.getId()))
            throw new IllegalEndpoint("Only the assigned inspector can complete this inspection.");

        Airport airport = flight.getOriginAirport();

        if (airport == null || !inspection.getAirport().getId().equals(airport.getId())
                || !inspector.getAirport().getId().equals(airport.getId()) || airport.getOperator() == null
                || !inspector.getOperator().getId().equals(airport.getOperator().getId()))
            throw new IllegalEndpoint("You can only complete inspections at your own airport.");

        inspection.setStatus(result);
        inspection.setReason(reason);
        inspection.setCompletedAt(LocalDateTime.now());

        flightInspectionRepository.save(inspection);

        String description = "Inspector " + inspector.getId() + " completed inspection " + inspectionId
                + " for flight " + flightId + " with result " + result;

        auditLogService.addAuditLog(user, AuditLog.Action.FLIGHT_INSPECTION_COMPLETED,
                AuditLog.EntityType.FLIGHT_INSPECTION, inspectionId, description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.ok("Inspection completed with result " + result + ".");
    }

    @Transactional
    public ResponseEntity<?> requestReInspection(Long flightId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee admin = user.getAirlineEmployee();

        if (admin == null || admin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN || admin.getAirline() == null)
            throw new IllegalEndpoint("Only airline admins can request re-inspection.");

        Flight flight = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (flight.getAirline() == null || !flight.getAirline().getId().equals(admin.getAirline().getId()))
            throw new IllegalEndpoint("You can only request re-inspection for your airline's flights.");

        if (flight.getStatus() != Flight.FlightStatus.PENDING_CREW && flight.getStatus() != Flight.FlightStatus.ACTIVE)
            throw new InvalidInformationException("Re-inspection can only be requested before departure.");

        FlightInspection previousInspection = flightInspectionRepository.findFirstByFlight_IdOrderByIdDesc(flightId)
                .orElseThrow( () -> new InformationNotFoundException("No inspection exists for this flight."));

        if (previousInspection.getStatus() != FlightInspection.InspectionStatus.FAILED)
            throw new InvalidInformationException("Re-inspection requires the latest inspection to have failed.");

        Airport airport = flight.getOriginAirport();

        if (airport == null || airport.getOperator() == null)
            throw new InvalidInformationException("The departure airport must have an operator.");

        FlightInspection inspection = new FlightInspection();
        inspection.setFlight(flight);
        inspection.setAirport(airport);

        flightInspectionRepository.save(inspection);

        String description = "Airline admin requested inspection " + inspection.getId() + " for flight " + flightId
                + " after failed inspection " + previousInspection.getId();

        auditLogService.addAuditLog(user, AuditLog.Action.FLIGHT_REINSPECTION_REQUESTED,
                AuditLog.EntityType.FLIGHT_INSPECTION, inspection.getId(), description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body("Re-inspection requested. Inspection ID: " + inspection.getId());
    }

    @Transactional
    public ResponseEntity<?> getAirportInspections() {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airportEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirportEmployee admin = user.getAirportEmployee();

        if (admin == null || admin.getAirportRole() != AirportEmployee.AirportRole.ADMIN
                || admin.getAirport() == null || admin.getOperator() == null || admin.getAirport().getOperator() == null
                || !admin.getOperator().getId().equals(admin.getAirport().getOperator().getId()))
            throw new IllegalEndpoint("Only airport admins can view their airport's inspections.");

        List<FlightInspection> inspections = flightInspectionRepository.findByAirport_IdOrderByIdDesc(admin.getAirport().getId());

        return ResponseEntity.ok(toInspectionResponses(inspections));
    }

    @Transactional
    public ResponseEntity<?> getMyInspections() {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airportEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirportEmployee inspector = user.getAirportEmployee();

        if (inspector == null || inspector.getAirportRole() != AirportEmployee.AirportRole.INSPECTOR)
            throw new IllegalEndpoint("Only inspectors can access their inspection assignments.");

        List<FlightInspection> inspections = flightInspectionRepository.findByInspector_IdOrderByIdDesc(inspector.getId());

        return ResponseEntity.ok(toInspectionResponses(inspections));
    }

    @Transactional
    public ResponseEntity<?> getFlightInspections(Long flightId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee admin = user.getAirlineEmployee();

        if (admin == null || admin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN || admin.getAirline() == null)
            throw new IllegalEndpoint("Only airline admins can access this endpoint.");

        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (flight.getAirline() == null || !flight.getAirline().getId().equals(admin.getAirline().getId()))
            throw new IllegalEndpoint("You can only view inspections for your airline's flights.");

        List<FlightInspection> inspections = flightInspectionRepository.findByFlight_IdOrderByIdDesc(flightId);

        return ResponseEntity.ok(toInspectionResponses(inspections));
    }

    private List<FlightInspectionResponse> toInspectionResponses(List<FlightInspection> inspections) {
        List<FlightInspectionResponse> responses = new ArrayList<>();

        for (FlightInspection inspection : inspections) {
            responses.add(new FlightInspectionResponse(
                    inspection.getId(),
                    inspection.getFlight().getId(),
                    inspection.getAirport().getId(),
                    inspection.getInspector() == null ? null : inspection.getInspector().getId(),
                    inspection.getStatus(),
                    inspection.getReason(),
                    inspection.getCreatedAt(),
                    inspection.getCompletedAt()
            ));
        }

        return responses;
    }
}

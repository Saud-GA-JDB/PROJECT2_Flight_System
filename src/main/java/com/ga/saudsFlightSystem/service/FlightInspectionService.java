package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.repository.AirportEmployeeRepository;
import com.ga.saudsFlightSystem.repository.FlightInspectionRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
}

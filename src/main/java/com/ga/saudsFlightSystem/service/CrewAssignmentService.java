package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest;
import com.ga.saudsFlightSystem.model.request.response.CrewAssignmentResponse;
import com.ga.saudsFlightSystem.model.request.response.getCrewAssignmentsResponse;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.CrewAssignmentRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class CrewAssignmentService {
    private CrewAssignmentRepository crewAssignmentRepository;
    private AirlineEmployeeRepository airlineEmployeeRepository;
    private FlightRepository flightRepository;
    private AuditLogService auditLogService;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private NotificationService notificationService;

    private static final Logger logger = Logger.getLogger(CrewAssignmentService.class.getName());

    public void checkAvailability(AirlineEmployee employee, LocalDateTime scheduledDeparture, LocalDateTime scheduledArrival) {
        boolean hasOverlappingAssignment = crewAssignmentRepository.hasOverlappingAssignment(employee.getId(), scheduledDeparture, scheduledArrival, Flight.FlightStatus.CANCELLED);

        if (hasOverlappingAssignment) {
            logger.warning("Crew assignment rejected for employee with id " + employee.getId() + " because flight times overlap");
            throw new InvalidInformationException("Assignment Time Conflict. This employee is assigned to another flight that overlap with this one.");
        }
    }

    @Transactional
    public ResponseEntity<?> addCrewAssignment(Long flightId, AddCrewAssignmentRequest request) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = user.getAirlineEmployee();
        if (airlineAdmin == null || airlineAdmin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (request.getEmployeeId() == null || request.getDuty() == null)
            throw new InvalidInformationException("Please fill in all the fields.");

        Flight flight = flightRepository.findByIdForCrewWrite(flightId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (!Objects.equals(flight.getAirline().getId(), airlineAdmin.getAirline().getId()))
            throw new InvalidInformationException("You are not allowed to edit flights that don't belong to your airline.");

        if (!(flight.getStatus() == Flight.FlightStatus.ACTIVE || flight.getStatus() == Flight.FlightStatus.PENDING_CREW) ||
                !flight.getScheduledDeparture().isAfter(LocalDateTime.now()))
            throw new InvalidInformationException("Crew assignments can only be done to pending or active flights.");

        AirlineEmployee employee = airlineEmployeeRepository.findByIdForAssignment(request.getEmployeeId())
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find employee with this id."));

        if (!Objects.equals(employee.getAirline().getId(), airlineAdmin.getAirline().getId()))
            throw new InvalidInformationException("Cannot assign employees that do not belong to your airline.");

        if (request.getDuty() == Duty.PILOT || request.getDuty() == Duty.SECONDARY_PILOT) {
            if (employee.getAirlineRole() != AirlineEmployee.AirlineRole.PILOT)
                throw new InvalidInformationException("Only pilots can be assigned pilot duties");
        } else if (request.getDuty() == Duty.FLIGHT_ATTENDANT) {
            if (employee.getAirlineRole() != AirlineEmployee.AirlineRole.FLIGHT_ATTENDANT)
                throw new InvalidInformationException("Only flight attendants can be assigned this duty");
        }

        if (crewAssignmentRepository.existsByFlightIdAndAirlineEmployeeId(flightId, employee.getId()))
            throw new InvalidInformationException("This employee is already assigned to this flight");

        checkAvailability(employee, flight.getScheduledDeparture(), flight.getScheduledArrival());

        CrewAssignment assignment = new CrewAssignment();
        assignment.setFlight(flight);
        assignment.setAirlineEmployee(employee);
        assignment.setDuty(request.getDuty());

        crewAssignmentRepository.save(assignment);

        User employeeUser = userRepository.findByAirlineEmployeeId(employee.getId());
        if (employeeUser != null) {
            eventPublisher.publishEvent(new Email(
                    employeeUser.getEmailAddress(),
                    "New Crew Assignment",
                    "You have been assigned to flight " + flight.getFlightNumber()
                            + " with duty " + assignment.getDuty()));
        }


        String description = "Airline admin assigned employee with id " + employee.getId() + " to flight with id " + flightId + " with duty " + assignment.getDuty();
        auditLogService.addAuditLog(user, AuditLog.Action.CREW_ASSIGNMENT_CREATED, AuditLog.EntityType.CREW_ASSIGNMENT, assignment.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");

        notificationService.sendToUser(
                user.getId(),
                "Assignment_Created",
                "employee " + employee.getFName()+" "+employee.getLName() + " assigned to Flight " +
                        flight.getFlightNumber() + " as " + assignment.getDuty()
        );
        notificationService.sendToUser(
                employeeUser.getId(),
                "Flight_Assignment",
                "You are assigned to flight " + flight.getFlightNumber() + " as " + assignment.getDuty() +
                        " please check you assignment to view more details"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CrewAssignmentResponse(assignment.getId(), flight.getId(), employee.getId(), assignment.getDuty(), assignment.getAssignedAt())
        );
    }

    @Transactional
    public ResponseEntity<?> getCrewAssignments(Long flightId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = user.getAirlineEmployee();

        if (airlineAdmin == null || airlineAdmin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        Flight flight = flightRepository.findByIdForCrewRead(flightId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find flight with this id."));

        if (!Objects.equals(airlineAdmin.getAirline().getId(), flight.getAirline().getId()))
            throw new InvalidInformationException("You arent allowed to view other airlines assignments!");

        List<getCrewAssignmentsResponse> assignmentsResponseList = new ArrayList<>();

        List<CrewAssignment> crewAssignmentList = crewAssignmentRepository.findByFlightId(flightId);

        for (CrewAssignment assignment: crewAssignmentList) {
            assignmentsResponseList.add(
                    new getCrewAssignmentsResponse(assignment.getAirlineEmployee().getId(), assignment.getDuty(), assignment.getAssignedAt())
            );
        }

        String description = "Airline admin viewed " + assignmentsResponseList.size() + " crew assignments for flight with id " + flightId;
        auditLogService.addAuditLog(user, AuditLog.Action.CREW_ASSIGNMENTS_VIEWED, AuditLog.EntityType.FLIGHT, flightId, description);
        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.OK).body(assignmentsResponseList);
    }

    @Transactional
    public ResponseEntity<?> removeCrewAssignment(Long flightId, Long employeeId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = user.getAirlineEmployee();
        if (airlineAdmin == null || airlineAdmin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        Flight flight = flightRepository.findByIdForCrewWrite(flightId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find flight with this id."));

        if (!Objects.equals(flight.getAirline().getId(), airlineAdmin.getAirline().getId()))
            throw new InvalidInformationException("You are not allowed to edit flights that don't belong to your airline.");

        if (!(flight.getStatus() == Flight.FlightStatus.ACTIVE ||  flight.getStatus() == Flight.FlightStatus.PENDING_CREW) || !flight.getScheduledDeparture().isAfter(LocalDateTime.now()))
            throw new InvalidInformationException("Crew can only be removed from active or pending crew flights.");



        AirlineEmployee employee = airlineEmployeeRepository.findByIdForAssignment(employeeId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find employee with this id."));

        CrewAssignment assignment = crewAssignmentRepository.findByFlightIdAndAirlineEmployeeId(flightId, employeeId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find employee with this id associated with this flight!"));

        if (flight.getStatus() == Flight.FlightStatus.ACTIVE) {
            crewAssignmentRepository.delete(assignment);
            List<CrewAssignment> list = crewAssignmentRepository.findByFlightId(flightId);
            if (!hasRequiredCrew(list))
                throw new InvalidInformationException("You can't remove this employee from the flight. please add an alternative employee before attempting to remove again.");
        } else {
            crewAssignmentRepository.delete(assignment);
        }

        String description = "Airline admin removed employee with id " + employeeId + " from flight with id " + flightId + " with duty " + assignment.getDuty();
        auditLogService.addAuditLog(user, AuditLog.Action.CREW_ASSIGNMENT_REMOVED, AuditLog.EntityType.CREW_ASSIGNMENT, assignment.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");

        notificationService.sendToUser(
                user.getId(),
                "Assignment_Removed",
                "employee " + employee.getFName()+" "+employee.getLName() + " removed from Flight " +
                        flight.getFlightNumber()
        );
        notificationService.sendToUser(
                employee.getId(),
                "Flight_Assignment",
                "You are removed from flight " + flight.getFlightNumber() + " as " + assignment.getDuty()
        );

        return ResponseEntity.noContent().build();
    }

    // helper methods
    protected boolean hasRequiredCrew(List<CrewAssignment> assignments) {
        boolean hasPilot = assignments.stream().anyMatch(a -> a.getDuty() == Duty.PILOT);
        boolean hasCopilot = assignments.stream().anyMatch(a -> a.getDuty() == Duty.SECONDARY_PILOT);
        boolean hasAttendant = assignments.stream().anyMatch(a -> a.getDuty() == Duty.FLIGHT_ATTENDANT);

        return hasPilot && hasCopilot && hasAttendant;
    }

    public boolean hasRequiredCrew(Long flightId) {
        return hasRequiredCrew(crewAssignmentRepository.findByFlightId(flightId));
    }
}

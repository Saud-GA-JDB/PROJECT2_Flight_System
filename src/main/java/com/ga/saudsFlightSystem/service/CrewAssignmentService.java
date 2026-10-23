package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddCrewAssignmentRequest;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.CrewAssignmentRepository;
import com.ga.saudsFlightSystem.repository.FlightRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@AllArgsConstructor
public class CrewAssignmentService {
    private CrewAssignmentRepository crewAssignmentRepository;
    private AirlineEmployeeRepository airlineEmployeeRepository;
    private FlightRepository flightRepository;

    public void checkAvailability(AirlineEmployee employee, LocalDateTime scheduledDeparture, LocalDateTime scheduledArrival) {
        boolean hasOverlappingAssignment = crewAssignmentRepository.hasOverlappingAssignment(employee.getId(), scheduledDeparture, scheduledArrival, Flight.FlightStatus.CANCELLED);

        if (hasOverlappingAssignment)
            throw new InvalidInformationException("Assignment Time Conflict. This employee is assigned to another flight that overlap with this one.");
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

        Flight flight = flightRepository.findById(flightId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find a flight with this ID."));

        if (!Objects.equals(flight.getAirline().getId(), airlineAdmin.getAirline().getId()))
            throw new InvalidInformationException("You are not allowed to edit flights that don't belong to your airline.");

        if (flight.getStatus() != Flight.FlightStatus.ACTIVE || !flight.getScheduledDeparture().isAfter(LocalDateTime.now()))
            throw new InvalidInformationException("Crew assignments can only be done to open flights.");

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

        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }
}

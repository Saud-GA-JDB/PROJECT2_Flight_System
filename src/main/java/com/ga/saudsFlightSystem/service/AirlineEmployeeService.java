package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddAirlineAdminRequest;
import com.ga.saudsFlightSystem.model.request.AddFlightRequest;
import com.ga.saudsFlightSystem.model.request.response.AddFlightResponse;
import com.ga.saudsFlightSystem.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.logging.Logger;

import static com.ga.saudsFlightSystem.model.Flight.FlightStatus.ACTIVE;

@Service
@AllArgsConstructor
public class AirlineEmployeeService {
    AirlineRepository airlineRepository;
    UserRepository userRepository;
    AirlineEmployeeRepository airlineEmployeeRepository;
    AirportRepository airportRepository;
    AirplaneRepository airplaneRepository;
    FlightRepository flightRepository;
    AirplaneService airplaneService;
    PasswordEncoder passwordEncoder;
    PhoneValidationService phoneValidationService;
    PendingRegistrationService pendingRegistrationService;
    AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(AirlineEmployeeService.class.getName());

    @Transactional
    public ResponseEntity<?> addAirlineAdmin(Long airlineId, AddAirlineAdminRequest request) {
        User user = UserService.getCurrentLoggedInUser();
        if(!UserService.isAllowedEndpoint("faaadmin", UserService.getCurrentLoggedInUser().getRole())) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        //validation
        if (request.getCpr() == null || !request.getCpr().matches("[0-9]{9}")) {
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");
        }
        if (!EmailService.validateEmailFormat(request.getEmailAddress())) {
            throw new InvalidInformationException("invalid Email Format");
        }
        if (request.getFName() == null || request.getFName().isBlank()
                || request.getLName() == null || request.getLName().isBlank()) {
            throw new InvalidInformationException("First name and last name are required.");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()
                || request.getPhoneNumberOpeningCode() == null
                || request.getPhoneNumberOpeningCode().isBlank()) {
            throw new InvalidInformationException("Phone number and opening code are required.");
        }
        if (!phoneValidationService.isValidPhoneNumber(request.getPhoneNumber(), request.getPhoneNumberOpeningCode())) {
            throw new InvalidInformationException("Invalid phone number or country code.");
        }
        if (request.getSalary() == null || request.getSalary() <= 0) {
            throw new InvalidInformationException("Salary must be greater than zero.");
        }
        if (request.getHireDate() == null || request.getHireDate().isBlank()) {
            throw new InvalidInformationException("Hire date is required.");
        }
        LocalDate hireDate;
        try {
            hireDate = LocalDate.parse(request.getHireDate());
        } catch (DateTimeParseException e) {
            throw new InvalidInformationException("Hire date must be a valid date in yyyy-MM-dd format.");
        }
        if (request.getSecurityQuestion() == null || request.getSecurityQuestion().isBlank()
                || request.getSecurityQuestionAnswer() == null
                || request.getSecurityQuestionAnswer().isBlank()) {
            throw new InvalidInformationException("Security question and answer are required.");
        }
        pendingRegistrationService.checkExistingDetails(request.getEmailAddress(), request.getCpr());


        AirlineEmployee airlineEmployee = new AirlineEmployee();
        airlineEmployee.setAirline(airlineRepository.findById(airlineId).orElseThrow(()-> new InformationNotFoundException("couldn't find the airline with this id")));
        airlineEmployee.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        airlineEmployee.setSalary(request.getSalary());
        airlineEmployee.setCpr(request.getCpr());
        airlineEmployee.setFName(request.getFName());
        airlineEmployee.setLName(request.getLName());
        airlineEmployee.setHireDate(hireDate);
        airlineEmployee.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode());
        airlineEmployee.setPhoneNumber(request.getPhoneNumber());
        User newUser = new User();
        newUser.setPassword(passwordEncoder.encode(request.getCpr()));
        newUser.setRole(User.Role.AIRLINE_EMPLOYEE);
        newUser.setEmailAddress(request.getEmailAddress());
        newUser.setSecurityQuestion(request.getSecurityQuestion());
        newUser.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase()));
        newUser.setAirlineEmployee(airlineEmployee);
        newUser.setActive(true);
        userRepository.save(newUser);
        newUser.setStatus(User.Status.ACTIVE);
        airlineEmployeeRepository.save(airlineEmployee);
        String description = "FAA admin created airline admin with user id " + newUser.getId() + " for airline with id " + airlineId;
        auditLogService.addAuditLog(user, AuditLog.Action.AIRLINE_ADMIN_CREATED, AuditLog.EntityType.USER, newUser.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");
        request.setSecurityQuestionAnswer(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @Transactional
    public ResponseEntity<?> addFlight(Long airplaneId, AddFlightRequest request) {
        User user = UserService.getCurrentLoggedInUser();
        if(!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()) ) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        AirlineEmployee airlineEmployee = user.getAirlineEmployee();
        if (airlineEmployee == null || airlineEmployee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN) {
            throw new IllegalEndpoint("Only airline admins can add flights!");
        }
        // validation
        if (    request.getOriginAirportIataCode()==null || request.getOriginAirportIataCode().isBlank() ||
                request.getArrivalAirportIataCode()==null || request.getArrivalAirportIataCode().isBlank() ||
                request.getScheduledArrival()==null || request.getScheduledDeparture()==null
        ) throw new InvalidInformationException("Please fill in all the required fields");
        if (!request.getScheduledDeparture().isAfter(LocalDateTime.now()))
            throw new InvalidInformationException("Departure Time must be in the future");
        if (!airportRepository.existsByIataCode(request.getArrivalAirportIataCode()))
            throw new InvalidInformationException("Please put a valid arrival airport");
        if (!airportRepository.existsByIataCode(request.getOriginAirportIataCode()))
            throw new InvalidInformationException("Please put a valid origin airport");
        if (request.getScheduledArrival().isBefore(request.getScheduledDeparture()))
            throw new InvalidInformationException("Arrival Time can't be before Departure Time");
        if (request.getScheduledDeparture().isEqual(request.getScheduledArrival()))
            throw new InvalidInformationException("Arrival Time can't be the same as Departure Time");
        if (request.getArrivalAirportIataCode().equalsIgnoreCase(request.getOriginAirportIataCode()))
            throw new InvalidInformationException("Origin and Arrival Airports can't be the same");


        Flight flight = new Flight();
        flight.setAirline(user.getAirlineEmployee().getAirline());
        flight.setStatus(ACTIVE);
        Airplane airplane = airplaneRepository.findById(airplaneId).orElseThrow(()-> new InformationNotFoundException("couldn't find airplane with this id"));
        flight.setAirplane(airplane);
        flight.setFirstClassSeatsCount(airplane.getFirstClassSeatsCapacity());
        flight.setStandardSeatsCount(airplane.getStandardSeatsCapacity());
        flight.setScheduledArrival(request.getScheduledArrival());
        flight.setScheduledDeparture(request.getScheduledDeparture());
        Airport arrivalAirport = airportRepository.findByIataCode(request.getArrivalAirportIataCode());
        flight.setDestinationAirport(arrivalAirport);
        Airport originAirport = airportRepository.findByIataCode(request.getOriginAirportIataCode());
        flight.setOriginAirport(originAirport);

        if (!Objects.equals(airplane.getAirline().getId(), airlineEmployee.getAirline().getId()))
            throw new InvalidInformationException("airplane must be owned by the airline");
        airplaneService.checkAvailability(airplane, request.getScheduledDeparture(), request.getScheduledArrival());

        flight = flightRepository.save(flight);
        flight.setFlightNumber(flight.getAirline().getAirlineCode() + flight.getId());
        flightRepository.save(flight);

        String description = "Airline admin created flight with id " + flight.getId();
        auditLogService.addAuditLog(user, AuditLog.Action.FLIGHT_CREATED, AuditLog.EntityType.FLIGHT, flight.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(new AddFlightResponse(
                flight.getFlightNumber(), airplane.getRegistrationNumber(),
                originAirport.getIataCode(), arrivalAirport.getIataCode(),
                flight.getScheduledDeparture(), flight.getScheduledArrival(), flight.getStatus()));

    }
}

package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.identity.EmployeeAccountProvisioner;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest.AccountType;
import com.ga.saudsFlightSystem.identity.ProvisionedAccount;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddAirlineAdminRequest;
import com.ga.saudsFlightSystem.model.request.AddAirlineEmployeeRequest;
import com.ga.saudsFlightSystem.model.request.response.AirlineEmployeeResponse;
import com.ga.saudsFlightSystem.model.request.AddFlightRequest;
import com.ga.saudsFlightSystem.model.request.response.AddFlightResponse;
import com.ga.saudsFlightSystem.repository.*;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.saudsFlightSystem.validation.EmailValidation;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Locale;
import java.util.logging.Logger;

import static com.ga.saudsFlightSystem.model.Flight.FlightStatus.PENDING_CREW;

@Service
@AllArgsConstructor
public class AirlineEmployeeService {
    AirlineRepository airlineRepository;
    private EmployeeAccountProvisioner employeeAccountProvisioner;
    AirlineEmployeeRepository airlineEmployeeRepository;
    AirportRepository airportRepository;
    AirplaneRepository airplaneRepository;
    FlightRepository flightRepository;
    AirplaneService airplaneService;
    PhoneValidationService phoneValidationService;
    private PasswordService passwordService;
    NotificationService notificationService;
    AuditLogService auditLogService;
    private CurrentActorProvider currentActorProvider;

    private static final Logger logger = Logger.getLogger(AirlineEmployeeService.class.getName());

    @Transactional
    public ResponseEntity<AirlineEmployeeResponse> addAirlineEmployee(AirlineEmployee.AirlineRole role, AddAirlineEmployeeRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()) || !"AIRLINE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("Only active airline admins can create employees.");

        AirlineEmployee admin = airlineEmployeeRepository
                .findByUserIdAndRole(actor.userId(), AirlineEmployee.AirlineRole.ADMIN)
                .orElseThrow(() -> new IllegalEndpoint("Only airline admins can create employees."));
        if (admin.getAirline() == null)
            throw new IllegalEndpoint("The admin must belong to an airline.");
        if (role != AirlineEmployee.AirlineRole.PILOT && role != AirlineEmployee.AirlineRole.FLIGHT_ATTENDANT)
            throw new InvalidInformationException("Choose PILOT or FLIGHT_ATTENDANT.");

        LocalDate hireDate = validateAddAirlineEmployeeRequest(request);
        employeeAccountProvisioner.checkExistingDetails(request.getEmailAddress().trim(), request.getCpr());

        AirlineEmployee employee = new AirlineEmployee();
        employee.setAirline(admin.getAirline());
        employee.setAirlineRole(role);
        employee.setHireDate(hireDate);
        employee.setSalary(request.getSalary());
        airlineEmployeeRepository.save(employee);

        ProvisionedAccount account = employeeAccountProvisioner.createAccount(
                new EmployeeAccountRequest(
                        AccountType.AIRLINE_EMPLOYEE,
                        employee.getId(),
                        request.getEmailAddress().trim(),
                        request.getPassword(),
                        request.getSecurityQuestion().trim(),
                        request.getSecurityQuestionAnswer().trim().toLowerCase(Locale.ROOT),
                        request.getFName().trim(),
                        request.getLName().trim(),
                        request.getCpr(),
                        request.getPhoneNumberOpeningCode().trim(),
                        request.getPhoneNumber().trim()
                ));
        employee.setUserId(account.userId());

        String description = "Airline admin created employee with id " + employee.getId()
                + " and role " + role + " for airline with id " + admin.getAirline().getId();
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.AIRLINE_EMPLOYEE_CREATED, AuditLog.EntityType.USER, account.userId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AirlineEmployeeResponse(
                        employee.getId(), account.userId(), admin.getAirline().getId(),
                        request.getFName().trim(), request.getLName().trim(), account.emailAddress(),
                        employee.getAirlineRole(), employee.getHireDate(), employee.getSalary()
                ));
    }

    private LocalDate validateAddAirlineEmployeeRequest(AddAirlineEmployeeRequest request) {
        if (request == null)
            throw new InvalidInformationException("Please fill in all the required fields.");
        checkEmployeeField(request.getFName());
        checkEmployeeField(request.getLName());
        checkEmployeeField(request.getEmailAddress());
        checkEmployeeField(request.getPhoneNumberOpeningCode());
        checkEmployeeField(request.getPhoneNumber());
        checkEmployeeField(request.getSecurityQuestion());
        checkEmployeeField(request.getSecurityQuestionAnswer());

        if (request.getCpr() == null || !request.getCpr().matches("[0-9]{9}"))
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");
        if (!EmailValidation.isValid(request.getEmailAddress().trim()))
            throw new InvalidInformationException("Invalid email format.");
        if (!phoneValidationService.isValidPhoneNumber(request.getPhoneNumber().trim(), request.getPhoneNumberOpeningCode().trim()))
            throw new InvalidInformationException("Invalid phone number or country code.");
        if (request.getPassword() == null || !passwordService.isValidPassword(request.getPassword()))
            throw new InvalidInformationException("Password must contain 8–30 characters, an uppercase letter, a digit, and no whitespace.");
        if (request.getSalary() == null || request.getSalary() <= 0)
            throw new InvalidInformationException("Salary must be greater than zero.");
        if (request.getHireDate() == null || request.getHireDate().isBlank())
            throw new InvalidInformationException("Hire date is required.");
        try {
            return LocalDate.parse(request.getHireDate().trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidInformationException("Hire date must be a valid date in yyyy-MM-dd format.");
        }
    }

    private void checkEmployeeField(String field) {
        if (field == null || field.isBlank())
            throw new InvalidInformationException("Please fill in all the required fields.");
        if (field.trim().length() > 255)
            throw new InvalidInformationException("Fields must not exceed 255 characters.");
    }

    /**
     * Lets an FAA admin create an airline admin account with CPR as its password.
     *
     * @param airlineId airline the new admin will belong to
     * @param request account and employee details
     * @return the submitted details without the security answer
     */
    @Transactional
    public ResponseEntity<?> addAirlineAdmin(Long airlineId, AddAirlineAdminRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!"FAA_ADMIN".equals(actor.role()) )
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");

        //validation
        if (request.getCpr() == null || !request.getCpr().matches("[0-9]{9}")) {
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");
        }
        if (!EmailValidation.isValid(request.getEmailAddress())) {
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
        employeeAccountProvisioner.checkExistingDetails(request.getEmailAddress(), request.getCpr());


        AirlineEmployee airlineEmployee = new AirlineEmployee();
        airlineEmployee.setAirline(airlineRepository.findById(airlineId).orElseThrow(()-> new InformationNotFoundException("couldn't find the airline with this id")));
        airlineEmployee.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        airlineEmployee.setSalary(request.getSalary());
        airlineEmployee.setHireDate(hireDate);
        airlineEmployeeRepository.save(airlineEmployee);

        ProvisionedAccount account = employeeAccountProvisioner.createAccount(
                new EmployeeAccountRequest(
                        AccountType.AIRLINE_EMPLOYEE,
                        airlineEmployee.getId(),
                        request.getEmailAddress(),
                        request.getCpr(),
                        request.getSecurityQuestion(),
                        request.getSecurityQuestionAnswer().trim().toLowerCase(),
                        request.getFName(),
                        request.getLName(),
                        request.getCpr(),
                        request.getPhoneNumberOpeningCode(),
                        request.getPhoneNumber()
                ));
        airlineEmployee.setUserId(account.userId());
        String description = "FAA admin created airline admin with user id " + account.userId() + " for airline with id " + airlineId;
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.AIRLINE_ADMIN_CREATED, AuditLog.EntityType.USER, account.userId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");
        request.setSecurityQuestionAnswer(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    /**
     * Creates a flight after checking the route, times and airplane availability.
     *
     * @param airplaneId airplane assigned to the flight
     * @param request airports and scheduled flight times
     * @return the new flight details
     */
    @Transactional
    public ResponseEntity<?> addFlight(Long airplaneId, AddFlightRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if(!"AIRLINE_EMPLOYEE".equals(actor.role()) ) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        AirlineEmployee airlineEmployee = airlineEmployeeRepository.findByUserId(actor.userId()).orElse(null);
        if (airlineEmployee == null || airlineEmployee.getAirline() == null || airlineEmployee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN) {
            throw new IllegalEndpoint("Only airline admins can add flights!");
        }

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


        if (request.getFirstClassPrice() == null || request.getFirstClassPrice() <= 0 ||
                request.getStandardPrice() == null || request.getStandardPrice() <= 0)
            throw new InvalidInformationException("Please enter positive prices for both seat classes");

        if (request.getCurrency() == null)
            throw new InvalidInformationException("Please choose a currency");

        Flight flight = new Flight();
        flight.setFirstClassPrice(request.getFirstClassPrice());
        flight.setStandardPrice(request.getStandardPrice());
        flight.setCurrency(request.getCurrency());
        flight.setAirline(airlineEmployee.getAirline());
        flight.setStatus(PENDING_CREW);
        Airplane airplane = airplaneRepository.findByIdForUpdate(airplaneId)
                .orElseThrow(() -> new InformationNotFoundException("couldn't find airplane with this id"));
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
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.FLIGHT_CREATED, AuditLog.EntityType.FLIGHT, flight.getId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");

        notificationService.sendToUser(
                actor.userId(),
                "FLIGHT_CREATED",
                "Flight " + flight.getFlightNumber() + " added successfully."
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new AddFlightResponse(
                flight.getFlightNumber(), airplane.getRegistrationNumber(),
                originAirport.getIataCode(), arrivalAirport.getIataCode(),
                flight.getScheduledDeparture(), flight.getScheduledArrival(), flight.getStatus()));

    }
}

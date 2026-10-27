package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airport;
import com.ga.saudsFlightSystem.model.AirportEmployee;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AddAirportEmployeeRequest;
import com.ga.saudsFlightSystem.model.request.response.AirportEmployeeResponse;
import com.ga.saudsFlightSystem.repository.AirportRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class AirportEmployeeService {

    private AirportRepository airportRepository;
    private UserRepository userRepository;
    private PendingRegistrationService pendingRegistrationService;
    private PhoneValidationService phoneValidationService;
    private PasswordService passwordService;
    private PasswordEncoder passwordEncoder;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(AirportEmployeeService.class.getName());

    @Transactional
    public ResponseEntity<?> addAirportOperatorAdmin(Long airportId, AddAirportEmployeeRequest request) {

        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("faaadmin", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        //checks that the fields are populated and validates the cpr, email, phone number, and password
        validateAddAirportEmployeeRequest(request);

        Airport airport = airportRepository.findByIdForUpdate(airportId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an airport with this ID."));

        if (airport.getOperator() == null)
            throw new InvalidInformationException("You need an operator before.");

        pendingRegistrationService.checkExistingDetails(request.getEmailAddress().trim(), request.getCpr());

        AirportEmployee employee = new AirportEmployee();
        employee.setAirport(airport);
        employee.setOperator(airport.getOperator());
        employee.setAirportRole(AirportEmployee.AirportRole.ADMIN);
        employee.setFName(request.getFName().trim());
        employee.setLName(request.getLName().trim());
        employee.setCpr(request.getCpr());
        employee.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode().trim());
        employee.setPhoneNumber(request.getPhoneNumber().trim());

        User employeeUser = new User();
        employeeUser.setEmailAddress(request.getEmailAddress().trim());
        employeeUser.setPassword(passwordEncoder.encode(request.getPassword()));
        employeeUser.setSecurityQuestion(request.getSecurityQuestion().trim());
        employeeUser.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase()));
        employeeUser.setRole(User.Role.AIRPORT_EMPLOYEE);
        employeeUser.setStatus(User.Status.ACTIVE);
        employeeUser.setActive(true);
        employeeUser.setAirportEmployee(employee);

        userRepository.save(employeeUser);

        String description = "Created airport admin with id " + employee.getId()
                + " for airport with id " + airportId + " and operator with id " + airport.getOperator().getId();

        auditLogService.addAuditLog(user,
                AuditLog.Action.AIRPORT_EMPLOYEE_CREATED, AuditLog.EntityType.USER, employeeUser.getId(), description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AirportEmployeeResponse(
                        employee.getId(),
                        employeeUser.getId(),
                        employee.getOperator().getId(),
                        airportId,
                        employee.getFName(),
                        employee.getLName(),
                        employeeUser.getEmailAddress(),
                        employee.getAirportRole()
                )
        );
    }

    private void validateAddAirportEmployeeRequest(
            AddAirportEmployeeRequest request) {

        if (request == null)
            throw new InvalidInformationException("Please fill in all the required fields.");

        checkRequestFieldPopulated(request.getFName());
        checkRequestFieldPopulated(request.getLName());
        checkRequestFieldPopulated(request.getEmailAddress());
        checkRequestFieldPopulated(request.getPhoneNumberOpeningCode());
        checkRequestFieldPopulated(request.getPhoneNumber());
        checkRequestFieldPopulated(request.getSecurityQuestion());
        checkRequestFieldPopulated(request.getSecurityQuestionAnswer());

        if (request.getCpr() == null || !request.getCpr().matches("[0-9]{9}"))
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");

        if (!EmailService.validateEmailFormat(request.getEmailAddress().trim()))
            throw new InvalidInformationException("Invalid email format.");

        if (!phoneValidationService.isValidPhoneNumber(request.getPhoneNumber().trim(), request.getPhoneNumberOpeningCode().trim()))
            throw new InvalidInformationException("Invalid phone number or country code.");

        if (request.getPassword() == null || !passwordService.isValidPassword(request.getPassword()))
            throw new InvalidInformationException("Password must contain 8–30 characters, an uppercase letter, a digit, and no whitespace.");
    }

    private void checkRequestFieldPopulated(String field) {
        if (field == null || field.isBlank())
            throw new InvalidInformationException("Please fill in all the required fields.");

        if (field.trim().length() > 255)
            throw new InvalidInformationException("Fields must not exceed 255 characters.");
    }
}

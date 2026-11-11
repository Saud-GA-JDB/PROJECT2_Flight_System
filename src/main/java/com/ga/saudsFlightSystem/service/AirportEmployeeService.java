package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.identity.EmployeeAccountProvisioner;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest.AccountType;
import com.ga.saudsFlightSystem.identity.ProvisionedAccount;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import com.ga.common.security.CurrentActor;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airport;
import com.ga.saudsFlightSystem.model.AirportEmployee;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.request.AddAirportEmployeeRequest;
import com.ga.saudsFlightSystem.model.request.response.AirportEmployeeResponse;
import com.ga.saudsFlightSystem.repository.AirportEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirportRepository;
import com.ga.saudsFlightSystem.validation.EmailValidation;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class AirportEmployeeService {
    private CurrentActorProvider currentActorProvider;

    private AirportRepository airportRepository;
    private EmployeeAccountProvisioner employeeAccountProvisioner;
    private AirportEmployeeRepository airportEmployeeRepository;
    private PhoneValidationService phoneValidationService;
    private PasswordService passwordService;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(AirportEmployeeService.class.getName());

    @Transactional
    public ResponseEntity<?> addAirportOperatorAdmin(Long airportId, AddAirportEmployeeRequest request) {

        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"FAA_ADMIN".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        //checks that the fields are populated and validates the cpr, email, phone number, and password
        validateAddAirportEmployeeRequest(request);

        Airport airport = airportRepository.findByIdForUpdate(airportId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an airport with this ID."));

        if (airport.getOperator() == null)
            throw new InvalidInformationException("You need an operator before.");

        return createAirportEmployee(actor, airport, AirportEmployee.AirportRole.ADMIN, request);
    }

    @Transactional
    public ResponseEntity<?> addAirportEmployee(AirportEmployee.AirportRole role, AddAirportEmployeeRequest request) {

        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRPORT_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirportEmployee admin = airportEmployeeRepository.findByUserId(actor.userId()).orElse(null);

        if (admin == null || admin.getAirport() == null || admin.getAirportRole() != AirportEmployee.AirportRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (role != AirportEmployee.AirportRole.INSPECTOR && role != AirportEmployee.AirportRole.BAGGAGE_HANDLER)
            throw new InvalidInformationException("Choose INSPECTOR or BAGGAGE_HANDLER.");

        validateAddAirportEmployeeRequest(request);

        Airport airport = airportRepository.findByIdForUpdate(admin.getAirport().getId())
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an airport with this ID."));

        if (admin.getAirportRole() != AirportEmployee.AirportRole.ADMIN || admin.getAirport() == null
                || !admin.getAirport().getId().equals(airport.getId()) || admin.getOperator() == null
                || airport.getOperator() == null || !admin.getOperator().getId().equals(airport.getOperator().getId()))
            throw new IllegalEndpoint("Only an admin of this airport can create its employees.");

        return createAirportEmployee(actor, airport, role, request);
    }

    // helper methods
    private ResponseEntity<?> createAirportEmployee(CurrentActor actor, Airport airport, AirportEmployee.AirportRole role, AddAirportEmployeeRequest request) {
        Long airportId = airport.getId();

        employeeAccountProvisioner.checkExistingDetails(request.getEmailAddress().trim(), request.getCpr());

        AirportEmployee employee = new AirportEmployee();
        employee.setAirport(airport);
        employee.setOperator(airport.getOperator());
        employee.setAirportRole(role);

        airportEmployeeRepository.save(employee);

        ProvisionedAccount account = employeeAccountProvisioner.createAccount(
                new EmployeeAccountRequest(
                        AccountType.AIRPORT_EMPLOYEE,
                        employee.getId(),
                        request.getEmailAddress().trim(),
                        request.getPassword(),
                        request.getSecurityQuestion().trim(),
                        request.getSecurityQuestionAnswer().trim().toLowerCase(),
                        request.getFName().trim(),
                        request.getLName().trim(),
                        request.getCpr(),
                        request.getPhoneNumberOpeningCode().trim(),
                        request.getPhoneNumber().trim()
                ));
        employee.setUserId(account.userId());

        String description = "Created airport employee with id " + employee.getId() + " and role " + role
                + " for airport with id " + airportId + " and operator with id " + airport.getOperator().getId();

        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.AIRPORT_EMPLOYEE_CREATED, AuditLog.EntityType.USER, account.userId(), description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AirportEmployeeResponse(
                        employee.getId(),
                        account.userId(),
                        employee.getOperator().getId(),
                        airportId,
                        request.getFName().trim(),
                        request.getLName().trim(),
                        account.emailAddress(),
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

        if (!EmailValidation.isValid(request.getEmailAddress().trim()))
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

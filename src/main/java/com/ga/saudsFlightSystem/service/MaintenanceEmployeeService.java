package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.repository.MaintenanceEmployeeRepository;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import com.ga.saudsFlightSystem.security.CurrentActor;
import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.MaintenanceEmployee;
import com.ga.saudsFlightSystem.model.MaintenanceProvider;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceEmployeeRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceEmployeeResponse;
import com.ga.saudsFlightSystem.repository.MaintenanceProviderRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import com.ga.saudsFlightSystem.validation.EmailValidation;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class MaintenanceEmployeeService {
    private MaintenanceEmployeeRepository maintenanceEmployeeRepository;
    private CurrentActorProvider currentActorProvider;
    private MaintenanceProviderRepository maintenanceProviderRepository;
    private UserRepository userRepository;
    private PendingRegistrationService pendingRegistrationService;
    private PhoneValidationService phoneValidationService;
    private PasswordService passwordService;
    private PasswordEncoder passwordEncoder;
    private AuditLogService auditLogService;
    private static final Logger logger = Logger.getLogger(MaintenanceEmployeeService.class.getName());

    @Transactional
    public ResponseEntity<?> addMaintenanceAdmin(Long providerId, AddMaintenanceEmployeeRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"FAA_ADMIN".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        return createMaintenanceEmployee(actor, providerId, request, MaintenanceEmployee.MaintenanceRole.ADMIN);
    }

    @Transactional
    public ResponseEntity<?> addMaintenanceTechnician(AddMaintenanceEmployeeRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"MAINTENANCE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You ae not allowed this endpoint!");

        MaintenanceEmployee admin = maintenanceEmployeeRepository.findByUserId(actor.userId()).orElse(null);

        if (admin == null || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN || admin.getProvider() == null)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        return createMaintenanceEmployee(actor, admin.getProvider().getId(), request, MaintenanceEmployee.MaintenanceRole.TECHNICIAN);
    }

    // helper methods
    private ResponseEntity<?> createMaintenanceEmployee(CurrentActor actor, Long providerId, AddMaintenanceEmployeeRequest request, MaintenanceEmployee.MaintenanceRole role) {
        validateAddMaintenanceEmployeeRequest(request, role);

        MaintenanceProvider maintenanceProvider = maintenanceProviderRepository.findByIdForUpdate(providerId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find the maintenance provider."));

        // checks different repositories if this cpr and email exist already. if yes, throws an exception
        pendingRegistrationService.checkExistingDetails(request.getEmailAddress().trim(), request.getCpr());

        MaintenanceEmployee maintenanceEmployee = new MaintenanceEmployee();
        maintenanceEmployee.setProvider(maintenanceProvider);
        maintenanceEmployee.setMaintenanceRole(role);
        maintenanceEmployee.setFName(request.getFName().trim());
        maintenanceEmployee.setLName(request.getLName().trim());
        maintenanceEmployee.setCpr(request.getCpr());
        maintenanceEmployee.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode().trim());
        maintenanceEmployee.setPhoneNumber(request.getPhoneNumber().trim());
        if (request.getLicenseNumber() != null && !request.getLicenseNumber().isBlank()) {
            maintenanceEmployee.setLicenseNumber(request.getLicenseNumber().trim());
        }

        User maintenanceEmployeeUser = new User();
        maintenanceEmployeeUser.setEmailAddress(request.getEmailAddress().trim());
        maintenanceEmployeeUser.setPassword(passwordEncoder.encode(request.getPassword()));
        maintenanceEmployeeUser.setSecurityQuestion(request.getSecurityQuestion().trim());
        maintenanceEmployeeUser.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase()));
        maintenanceEmployeeUser.setRole(User.Role.MAINTENANCE_EMPLOYEE);
        maintenanceEmployeeUser.setStatus(User.Status.ACTIVE);
        maintenanceEmployeeUser.setActive(true);
        maintenanceEmployeeUser.setMaintenanceEmployee(maintenanceEmployee);

        userRepository.save(maintenanceEmployeeUser);

        String description = "Created maintenance employee with id " + maintenanceEmployee.getId() +
                " and role " + role + " for provider with id " + providerId;

        auditLogService.addAuditLogForActor(
                actor,
                AuditLog.Action.MAINTENANCE_EMPLOYEE_CREATED,
                AuditLog.EntityType.USER,
                maintenanceEmployeeUser.getId(),
                description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new MaintenanceEmployeeResponse(
                        maintenanceEmployee.getId(),
                        maintenanceEmployeeUser.getId(),
                        providerId,
                        maintenanceEmployee.getFName(),
                        maintenanceEmployee.getLName(),
                        maintenanceEmployeeUser.getEmailAddress(),
                        maintenanceEmployee.getMaintenanceRole(),
                        maintenanceEmployee.getLicenseNumber()
                )
        );

    }

    // a helper method that checks the fields and throws an error if some field is invalid.
    private void validateAddMaintenanceEmployeeRequest(AddMaintenanceEmployeeRequest request, MaintenanceEmployee.MaintenanceRole role) {
        if (request == null)
            throw new InvalidInformationException("fill the request fields.");

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

        if (role == MaintenanceEmployee.MaintenanceRole.TECHNICIAN) {
            checkRequestFieldPopulated(request.getLicenseNumber());
        } else if (request.getLicenseNumber() != null && request.getLicenseNumber().trim().length() > 255) {
            throw new InvalidInformationException("License number must not exceed 255 characters.");
        }

    }

    private void checkRequestFieldPopulated(String field) {
        if (field == null || field.isBlank())
            throw new InvalidInformationException("Please fill fill all the request fields.");

        if (field.trim().length() > 255)
            throw new InvalidInformationException("fields can't be that large!");
    }
}

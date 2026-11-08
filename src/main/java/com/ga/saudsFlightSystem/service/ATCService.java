package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddATCEmployeeRequest;
import com.ga.saudsFlightSystem.model.request.AddAirNavigationProviderRequest;
import com.ga.saudsFlightSystem.model.request.AddControlFacilityRequest;
import com.ga.saudsFlightSystem.model.request.response.*;
import com.ga.saudsFlightSystem.repository.*;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class ATCService {
    private AirNavigationServiceProviderRepository providerRepository;
    private ControlFacilityRepository facilityRepository;
    private ATCEmployeeRepository employeeRepository;
    private AirportRepository airportRepository;
    private UserRepository userRepository;
    private PendingRegistrationService pendingRegistrationService;
    private PhoneValidationService phoneValidationService;
    private PasswordService passwordService;
    private PasswordEncoder passwordEncoder;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(ATCService.class.getName());

    @Transactional
    public ResponseEntity<ProviderResponse> addProvider(AddAirNavigationProviderRequest request) {
        User user = requireFAAAdmin();
        if (request == null)
            throw new InvalidInformationException("Please fill in all the required fields.");
        checkField(request.getName(), 150);
        checkField(request.getContactEmail(), 254);
        if (!EmailService.validateEmailFormat(request.getContactEmail().trim()))
            throw new InvalidInformationException("Please enter a valid email address.");

        AirNavigationServiceProvider provider = new AirNavigationServiceProvider();
        provider.setName(request.getName().trim());
        provider.setContactEmail(request.getContactEmail().trim());
        providerRepository.save(provider);

        String description = "FAA admin created air navigation provider with id " + provider.getId();
        auditLogService.addAuditLog(user, AuditLog.Action.AIR_NAVIGATION_PROVIDER_CREATED,
                AuditLog.EntityType.AIR_NAVIGATION_PROVIDER, provider.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(providerResponse(provider));
    }

    @Transactional
    public ResponseEntity<ProviderListResponse> getProviders(int page, int size) {
        requireFAAAdmin();
        validatePagination(page, size);
        List<ProviderResponse> content = new ArrayList<>();
        for (AirNavigationServiceProvider provider : providerRepository.findProviders(size, (long) page * size))
            content.add(providerResponse(provider));
        long totalElements = providerRepository.count();
        return ResponseEntity.ok(new ProviderListResponse(content, page, size, totalElements, totalPages(totalElements, size)));
    }

    @Transactional
    public ResponseEntity<FacilityResponse> addFacility(Long providerId, AddControlFacilityRequest request) {
        User user = requireFAAAdmin();
        checkId(providerId);
        if (request == null)
            throw new InvalidInformationException("Please fill in all the required fields.");
        checkId(request.getAirportId());
        checkField(request.getName(), 150);

        Airport airport = airportRepository.findByIdForUpdate(request.getAirportId())
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an airport with this ID."));
        AirNavigationServiceProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an air navigation provider with this ID."));
        requireFAAAdmin();
        if (facilityRepository.existsByAirport_Id(airport.getId()))
            throw new InformationExistException("This airport already has a control tower.");

        ControlFacility facility = new ControlFacility();
        facility.setProvider(provider);
        facility.setAirport(airport);
        facility.setName(request.getName().trim());
        facilityRepository.save(facility);

        String description = "FAA admin created control facility with id " + facility.getId()
                + " for airport with id " + airport.getId() + " and provider with id " + providerId;
        auditLogService.addAuditLog(user, AuditLog.Action.CONTROL_FACILITY_CREATED,
                AuditLog.EntityType.CONTROL_FACILITY, facility.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(facilityResponse(facility));
    }

    @Transactional
    public ResponseEntity<FacilityListResponse> getFacilities(Long providerId, int page, int size) {
        requireFAAAdmin();
        checkId(providerId);
        validatePagination(page, size);
        if (!providerRepository.existsById(providerId))
            throw new InformationNotFoundException("Couldn't find an air navigation provider with this ID.");
        List<FacilityResponse> content = new ArrayList<>();
        for (ControlFacility facility : facilityRepository.findByProviderId(providerId, size, (long) page * size))
            content.add(facilityResponse(facility));
        long totalElements = facilityRepository.countByProvider_Id(providerId);
        return ResponseEntity.ok(new FacilityListResponse(content, page, size, totalElements, totalPages(totalElements, size)));
    }

    @Transactional
    public ResponseEntity<ATCEmployeeResponse> addAdmin(Long facilityId, AddATCEmployeeRequest request) {
        User user = requireFAAAdmin();
        checkId(facilityId);
        validateEmployee(request);
        ControlFacility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a control facility with this ID."));
        requireFAAAdmin();
        return createEmployee(user, facility, ATCEmployee.ATCRole.ADMIN, request);
    }

    @Transactional
    public ResponseEntity<ATCEmployeeResponse> addController(AddATCEmployeeRequest request) {
        User user = requireATCEmployee(true);
        validateEmployee(request);
        Long facilityId = user.getAtcEmployee().getFacility().getId();
        ControlFacility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a control facility with this ID."));
        user = requireATCEmployee(true);
        if (!facilityId.equals(user.getAtcEmployee().getFacility().getId()))
            throw new IllegalEndpoint("Only an admin of this tower can create its controllers.");
        return createEmployee(user, facility, ATCEmployee.ATCRole.CONTROLLER, request);
    }

    @Transactional
    public ResponseEntity<ATCEmployeeListResponse> getControllers(int page, int size) {
        User user = requireATCEmployee(true);
        validatePagination(page, size);
        Long facilityId = user.getAtcEmployee().getFacility().getId();
        List<ATCEmployeeResponse> content = new ArrayList<>();
        for (User controller : userRepository.findTowerControllers(facilityId, size, (long) page * size))
            content.add(employeeResponse(controller));
        long totalElements = userRepository.countTowerControllers(facilityId);
        return ResponseEntity.ok(new ATCEmployeeListResponse(content, page, size, totalElements, totalPages(totalElements, size)));
    }

    @Transactional
    public ResponseEntity<FacilityResponse> getOwnFacility() {
        User user = requireATCEmployee(false);
        return ResponseEntity.ok(facilityResponse(user.getAtcEmployee().getFacility()));
    }

    // helper methods
    private ResponseEntity<ATCEmployeeResponse> createEmployee(User user, ControlFacility facility,
                                                             ATCEmployee.ATCRole role, AddATCEmployeeRequest request) {
        pendingRegistrationService.checkExistingDetails(request.getEmailAddress().trim(), request.getCpr());
        String employeeNumber = request.getEmployeeNumber().trim().toUpperCase(Locale.ROOT);
        if (employeeRepository.existsByEmployeeNumber(employeeNumber))
            throw new InformationExistException("An ATC employee with this employee number already exists.");

        ATCEmployee employee = new ATCEmployee();
        employee.setFacility(facility);
        employee.setAtcRole(role);
        employee.setEmployeeNumber(employeeNumber);
        employee.setFName(request.getFName().trim());
        employee.setLName(request.getLName().trim());
        employee.setCpr(request.getCpr());
        employee.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode().trim());
        employee.setPhoneNumber(request.getPhoneNumber().trim());

        User employeeUser = new User();
        employeeUser.setEmailAddress(request.getEmailAddress().trim());
        employeeUser.setPassword(passwordEncoder.encode(request.getPassword()));
        employeeUser.setSecurityQuestion(request.getSecurityQuestion().trim());
        employeeUser.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase(Locale.ROOT)));
        employeeUser.setRole(User.Role.ATC_EMPLOYEE);
        employeeUser.setStatus(User.Status.ACTIVE);
        employeeUser.setActive(true);
        employeeUser.setAtcEmployee(employee);
        userRepository.save(employeeUser);

        String description = "Created ATC employee with id " + employee.getId() + " and role " + role
                + " for control facility with id " + facility.getId();
        auditLogService.addAuditLog(user, AuditLog.Action.ATC_EMPLOYEE_CREATED,
                AuditLog.EntityType.USER, employeeUser.getId(), description);
        logger.info(description + " (user id: " + user.getId() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeResponse(employeeUser));
    }

    private User requireActiveUser() {
        User user = UserService.getCurrentLoggedInUser();
        if (user != null && user.getId() != null)
            user = userRepository.findById(user.getId()).orElse(null);
        if (user == null || !user.isActive() || user.getStatus() != User.Status.ACTIVE)
            throw new IllegalEndpoint("An active account is required.");
        return user;
    }

    private User requireFAAAdmin() {
        User user = requireActiveUser();
        if (user.getRole() != User.Role.FAA_ADMIN || user.getFaaAdmin() == null)
            throw new IllegalEndpoint("Only FAA admins can manage air navigation providers and towers.");
        return user;
    }

    private User requireATCEmployee(boolean adminOnly) {
        User user = requireActiveUser();
        ATCEmployee employee = user.getAtcEmployee();
        if (user.getRole() != User.Role.ATC_EMPLOYEE || employee == null || employee.getFacility() == null
                || (employee.getAtcRole() != ATCEmployee.ATCRole.ADMIN && employee.getAtcRole() != ATCEmployee.ATCRole.CONTROLLER)
                || (adminOnly && employee.getAtcRole() != ATCEmployee.ATCRole.ADMIN))
            throw new IllegalEndpoint("You are not allowed this endpoint!");
        return user;
    }

    private void validateEmployee(AddATCEmployeeRequest request) {
        if (request == null)
            throw new InvalidInformationException("Please fill in all the required fields.");
        checkField(request.getFName(), 255);
        checkField(request.getLName(), 255);
        checkField(request.getEmailAddress(), 254);
        checkField(request.getPhoneNumberOpeningCode(), 255);
        checkField(request.getPhoneNumber(), 255);
        checkField(request.getSecurityQuestion(), 255);
        checkField(request.getSecurityQuestionAnswer(), 255);
        checkField(request.getEmployeeNumber(), 50);
        checkField(request.getEmployeeNumber().trim().toUpperCase(Locale.ROOT), 50);
        if (request.getCpr() == null || !request.getCpr().matches("[0-9]{9}"))
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");
        if (!EmailService.validateEmailFormat(request.getEmailAddress().trim()))
            throw new InvalidInformationException("Invalid email format.");
        if (!phoneValidationService.isValidPhoneNumber(request.getPhoneNumber().trim(), request.getPhoneNumberOpeningCode().trim()))
            throw new InvalidInformationException("Invalid phone number or country code.");
        if (request.getPassword() == null || !passwordService.isValidPassword(request.getPassword()))
            throw new InvalidInformationException("Password must contain 8–30 characters, an uppercase letter, a digit, and no whitespace.");
    }

    private void checkField(String value, int maxLength) {
        if (value == null || value.isBlank())
            throw new InvalidInformationException("Please fill in all the required fields.");
        if (value.trim().length() > maxLength)
            throw new InvalidInformationException("Field must not exceed " + maxLength + " characters.");
    }

    private void checkId(Long id) {
        if (id == null || id <= 0)
            throw new InvalidInformationException("IDs must be positive.");
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

    private ProviderResponse providerResponse(AirNavigationServiceProvider provider) {
        return new ProviderResponse(provider.getId(), provider.getName(), provider.getContactEmail());
    }

    private FacilityResponse facilityResponse(ControlFacility facility) {
        return new FacilityResponse(facility.getId(), facility.getProvider().getId(), facility.getAirport().getId(),
                facility.getName(), facility.getFacilityType());
    }

    private ATCEmployeeResponse employeeResponse(User user) {
        ATCEmployee employee = user.getAtcEmployee();
        return new ATCEmployeeResponse(employee.getId(), user.getId(), employee.getFacility().getId(),
                employee.getEmployeeNumber(), employee.getFName(), employee.getLName(), user.getEmailAddress(), employee.getAtcRole());
    }
}

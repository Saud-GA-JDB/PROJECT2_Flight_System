package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.identity.EmployeeAccountProvisioner;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest;
import com.ga.saudsFlightSystem.identity.EmployeeAccountRequest.AccountType;
import com.ga.saudsFlightSystem.identity.ProvisionedAccount;
import com.ga.saudsFlightSystem.identity.PersonalProfile;
import com.ga.saudsFlightSystem.identity.PersonalProfileStore;
import com.ga.saudsFlightSystem.repository.FAAAdminRepository;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import com.ga.saudsFlightSystem.security.CurrentActor;
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
import com.ga.saudsFlightSystem.validation.EmailValidation;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class ATCService {
    private FAAAdminRepository faaAdminRepository;
    private CurrentActorProvider currentActorProvider;
    private AirNavigationServiceProviderRepository providerRepository;
    private ControlFacilityRepository facilityRepository;
    private ATCEmployeeRepository employeeRepository;
    private AirportRepository airportRepository;
    private UserRepository userRepository;
    private EmployeeAccountProvisioner employeeAccountProvisioner;
    private PersonalProfileStore personalProfileStore;
    private PhoneValidationService phoneValidationService;
    private PasswordService passwordService;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(ATCService.class.getName());

    @Transactional
    public ResponseEntity<ProviderResponse> addProvider(AddAirNavigationProviderRequest request) {
        CurrentActor actor = requireFAAAdmin();
        if (request == null)
            throw new InvalidInformationException("Please fill in all the required fields.");
        checkField(request.getName(), 150);
        checkField(request.getContactEmail(), 254);
        if (!EmailValidation.isValid(request.getContactEmail().trim()))
            throw new InvalidInformationException("Please enter a valid email address.");

        AirNavigationServiceProvider provider = new AirNavigationServiceProvider();
        provider.setName(request.getName().trim());
        provider.setContactEmail(request.getContactEmail().trim());
        providerRepository.save(provider);

        String description = "FAA admin created air navigation provider with id " + provider.getId();
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.AIR_NAVIGATION_PROVIDER_CREATED,
                AuditLog.EntityType.AIR_NAVIGATION_PROVIDER, provider.getId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");
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
        CurrentActor actor = requireFAAAdmin();
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
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.CONTROL_FACILITY_CREATED,
                AuditLog.EntityType.CONTROL_FACILITY, facility.getId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");
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
        CurrentActor actor = requireFAAAdmin();
        checkId(facilityId);
        validateEmployee(request);
        ControlFacility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a control facility with this ID."));
        requireFAAAdmin();
        return createEmployee(actor, facility, ATCEmployee.ATCRole.ADMIN, request);
    }

    @Transactional
    public ResponseEntity<ATCEmployeeResponse> addController(AddATCEmployeeRequest request) {
        CurrentActor actor = requireActiveActor();
        ATCEmployee currentEmployee = requireATCEmployee(actor, true);
        validateEmployee(request);
        Long facilityId = currentEmployee.getFacility().getId();
        ControlFacility facility = facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find a control facility with this ID."));
        actor = requireActiveActor();
        currentEmployee = requireATCEmployee(actor, true);
        if (!facilityId.equals(currentEmployee.getFacility().getId()))
            throw new IllegalEndpoint("Only an admin of this tower can create its controllers.");
        return createEmployee(actor, facility, ATCEmployee.ATCRole.CONTROLLER, request);
    }

    @Transactional
    public ResponseEntity<ATCEmployeeListResponse> getControllers(int page, int size) {
        CurrentActor actor = requireActiveActor();
        ATCEmployee currentEmployee = requireATCEmployee(actor, true);
        validatePagination(page, size);
        Long facilityId = currentEmployee.getFacility().getId();
        List<User> controllers = userRepository.findTowerControllers(
                facilityId, size, (long) page * size);
        Map<Long, PersonalProfile> profiles = personalProfileStore.findByUserIds(
                controllers.stream().map(User::getId).toList());
        List<ATCEmployeeResponse> content = new ArrayList<>();
        for (User controller : controllers) {
            content.add(employeeResponse(controller, profiles.get(controller.getId())));
        }
        long totalElements = userRepository.countTowerControllers(facilityId);
        return ResponseEntity.ok(new ATCEmployeeListResponse(content, page, size, totalElements, totalPages(totalElements, size)));
    }

    @Transactional
    public ResponseEntity<FacilityResponse> getOwnFacility() {
        CurrentActor actor = requireActiveActor();
        ATCEmployee currentEmployee = requireATCEmployee(actor, false);
        return ResponseEntity.ok(facilityResponse(currentEmployee.getFacility()));
    }

    // helper methods
    private ResponseEntity<ATCEmployeeResponse> createEmployee(CurrentActor actor, ControlFacility facility,
                                                             ATCEmployee.ATCRole role, AddATCEmployeeRequest request) {
        employeeAccountProvisioner.checkExistingDetails(request.getEmailAddress().trim(), request.getCpr());
        String employeeNumber = request.getEmployeeNumber().trim().toUpperCase(Locale.ROOT);
        if (employeeRepository.existsByEmployeeNumber(employeeNumber))
            throw new InformationExistException("An ATC employee with this employee number already exists.");

        ATCEmployee employee = new ATCEmployee();
        employee.setFacility(facility);
        employee.setAtcRole(role);
        employee.setEmployeeNumber(employeeNumber);

        employeeRepository.save(employee);

        ProvisionedAccount account = employeeAccountProvisioner.createAccount(
                new EmployeeAccountRequest(
                        AccountType.ATC_EMPLOYEE,
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

        String description = "Created ATC employee with id " + employee.getId() + " and role " + role
                + " for control facility with id " + facility.getId();
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.ATC_EMPLOYEE_CREATED,
                AuditLog.EntityType.USER, account.userId(), description);
        logger.info(description + " (user id: " + actor.userId() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ATCEmployeeResponse(
                        employee.getId(),
                        account.userId(),
                        employee.getFacility().getId(),
                        employee.getEmployeeNumber(),
                        request.getFName().trim(),
                        request.getLName().trim(),
                        account.emailAddress(),
                        employee.getAtcRole()
                ));
    }

    private CurrentActor requireActiveActor() {
        CurrentActor actor = currentActorProvider.getFreshCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()))
            throw new IllegalEndpoint("An active account is required.");
        return actor;
    }

    private CurrentActor requireFAAAdmin() {
        CurrentActor actor = requireActiveActor();
        if (!"FAA_ADMIN".equals(actor.role()) || faaAdminRepository.findByUserId(actor.userId()).isEmpty())
            throw new IllegalEndpoint("Only FAA admins can manage air navigation providers and towers.");
        return actor;
    }

    private ATCEmployee requireATCEmployee(CurrentActor actor, boolean adminOnly) {
        ATCEmployee employee = employeeRepository.findByUserId(actor.userId()).orElse(null);
        if (!"ATC_EMPLOYEE".equals(actor.role()) || employee == null || employee.getFacility() == null
                || (employee.getAtcRole() != ATCEmployee.ATCRole.ADMIN && employee.getAtcRole() != ATCEmployee.ATCRole.CONTROLLER)
                || (adminOnly && employee.getAtcRole() != ATCEmployee.ATCRole.ADMIN))
            throw new IllegalEndpoint("You are not allowed this endpoint!");
        return employee;
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
        if (!EmailValidation.isValid(request.getEmailAddress().trim()))
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

    private ATCEmployeeResponse employeeResponse(User user, PersonalProfile profile) {
        if (profile == null) {
            throw new InformationNotFoundException("No profile information found for this user.");
        }
        ATCEmployee employee = user.getAtcEmployee();
        return new ATCEmployeeResponse(employee.getId(), user.getId(), employee.getFacility().getId(),
                employee.getEmployeeNumber(), profile.getFName(), profile.getLName(), user.getEmailAddress(), employee.getAtcRole());
    }
}

package com.ga.saudsFlightSystem.service;

import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationExistException;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceProviderRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceProviderResponse;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceContractRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceProviderRepository;
import com.ga.common.security.CurrentActor;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import com.ga.saudsFlightSystem.validation.EmailValidation;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class MaintenanceProviderService {
    private MaintenanceProviderRepository maintenanceProviderRepository;
    private MaintenanceContractRepository maintenanceContractRepository;
    private AuditLogService auditLogService;
    private CurrentActorProvider currentActorProvider;
    private AirlineEmployeeRepository airlineEmployeeRepository;
    private static final Logger logger = Logger.getLogger(MaintenanceProviderService.class.getName());

    @Transactional
    public ResponseEntity<?> addMaintenanceProvider(AddMaintenanceProviderRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"FAA_ADMIN".equals(actor.role()) )
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (request.getName() == null || request.getName().isBlank() || request.getContactEmail() == null || request.getContactEmail().isBlank())
            throw new InvalidInformationException("Please fill in all the required fields.");

        if (!EmailValidation.isValid(request.getContactEmail().trim()))
            throw new InvalidInformationException("Pleas put a valid email address.");

        if (request.getName().trim().length() > 255)
            throw new InvalidInformationException("name can't be that long");

        MaintenanceProvider maintenanceProvider = new MaintenanceProvider();
        maintenanceProvider.setName(request.getName().trim());
        maintenanceProvider.setContactEmail(request.getContactEmail().trim());

        maintenanceProviderRepository.save(maintenanceProvider);

        String description = "FAA admin created maintenance provider with id " + maintenanceProvider.getId();

        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.MAINTENANCE_PROVIDER_CREATED,
                AuditLog.EntityType.MAINTENANCE_PROVIDER,
                maintenanceProvider.getId(),
                description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new MaintenanceProviderResponse(maintenanceProvider.getId(), maintenanceProvider.getName(), maintenanceProvider.getContactEmail())
        );
    }

    @Transactional
    public ResponseEntity<?> contractMaintenanceProvider(Long providerId) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role()) )
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = airlineEmployeeRepository.findByUserIdAndRole(actor.userId(), AirlineEmployee.AirlineRole.ADMIN)
                .orElseThrow( () -> new IllegalEndpoint("only airline admins can access this endpoint"));

        MaintenanceProvider maintenanceProvider = maintenanceProviderRepository.findByIdForUpdate(providerId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find Maintenance provider with this id"));

        Long airlineId = airlineAdmin.getAirline().getId();

        if (maintenanceContractRepository.existsByProviderIdAndAirlineId(providerId, airlineId))
            throw new InformationExistException("Already contracted");

        MaintenanceContract contract = new MaintenanceContract();
        contract.setProviderId(providerId);
        contract.setAirlineId(airlineId);
        maintenanceContractRepository.save(contract);

        //TODO: send emails to maintenance provider and airline.

        String description = "Airline admin contracted maintenance provider with id " + maintenanceProvider.getId()
                        + " to airline with id " + airlineId;

        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.MAINTENANCE_PROVIDER_LINKED,
                AuditLog.EntityType.MAINTENANCE_PROVIDER,
                maintenanceProvider.getId(), description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(HttpStatus.OK).body(
                new MaintenanceProviderResponse(maintenanceProvider.getId(), maintenanceProvider.getName(), maintenanceProvider.getContactEmail())
        );
    }
}

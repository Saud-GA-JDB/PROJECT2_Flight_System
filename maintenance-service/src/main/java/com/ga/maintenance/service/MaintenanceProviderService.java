package com.ga.maintenance.service;

import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.InformationExistException;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.maintenance.model.*;
import com.ga.maintenance.model.request.AddMaintenanceProviderRequest;
import com.ga.maintenance.model.request.response.MaintenanceProviderResponse;
import com.ga.maintenance.client.AviationServiceClient;
import com.ga.maintenance.repository.MaintenanceContractRepository;
import com.ga.maintenance.repository.MaintenanceProviderRepository;
import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.maintenance.validation.EmailValidation;
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
    private AviationServiceClient aviationServiceClient;
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
    public ResponseEntity<?> contractMaintenanceProvider(Long providerId, String authorization) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role()) )
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        var scope = aviationServiceClient.getAirlineScope(authorization);
        if (!actor.userId().equals(scope.userId()))
            throw new IllegalEndpoint("The airline scope does not match the authenticated user.");

        MaintenanceProvider maintenanceProvider = maintenanceProviderRepository.findByIdForUpdate(providerId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find Maintenance provider with this id"));

        Long airlineId = scope.airlineId();

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

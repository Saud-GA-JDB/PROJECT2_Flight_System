package com.ga.maintenance.service;

import com.ga.common.contract.maintenance.MaintenanceAirlineResponse;
import com.ga.maintenance.model.request.response.MaintenanceContractResponse;
import com.ga.maintenance.repository.MaintenanceEmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
import com.ga.common.validation.EmailValidation;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class MaintenanceProviderService {
    private final MaintenanceProviderRepository maintenanceProviderRepository;
    private final MaintenanceContractRepository maintenanceContractRepository;
    private final AuditLogService auditLogService;
    private final CurrentActorProvider currentActorProvider;
    private final AviationServiceClient aviationServiceClient;
    private final MaintenanceEmployeeRepository maintenanceEmployeeRepository;
    @Value("${maintenance.notification-service-key:${MAINTENANCE_NOTIFICATION_SERVICE_KEY:}}")
    private String aviationServiceKey;

    private static final Logger logger = Logger.getLogger(MaintenanceProviderService.class.getName());

    @Transactional
    public List<MaintenanceProviderResponse> getFAAProviders() {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()) || !"FAA_ADMIN".equals(actor.role()))
            throw new IllegalEndpoint("Only active FAA admins can list maintenance providers.");

        return providerResponses(maintenanceProviderRepository.findAllByOrderByNameAscIdAsc());
    }

    @Transactional
    public List<MaintenanceProviderResponse> getAirlineProviders(boolean contractedOnly, String authorization) {
        Long airlineId = requireAirlineAdmin(authorization);
        return providerResponses(contractedOnly
                ? maintenanceProviderRepository.findContractedByAirlineId(airlineId)
                : maintenanceProviderRepository.findAllByOrderByNameAscIdAsc());
    }

    @Transactional
    public List<MaintenanceContractResponse> getAirlineContracts(String authorization) {
        Long airlineId = requireAirlineAdmin(authorization);
        return contractResponses(maintenanceContractRepository.findByAirlineIdOrderByIdAsc(airlineId));
    }

    @Transactional
    public List<MaintenanceContractResponse> getProviderContracts() {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()) || !"MAINTENANCE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("Only active maintenance admins can list contracts.");

        MaintenanceEmployee admin = maintenanceEmployeeRepository.findByUserId(actor.userId()).orElse(null);
        if (admin == null || admin.getProvider() == null || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN)
            throw new IllegalEndpoint("Only maintenance admins can list contracts.");

        return contractResponses(maintenanceContractRepository.findByProviderIdOrderByIdAsc(admin.getProvider().getId()));
    }

    private Long requireAirlineAdmin(String authorization) {
        CurrentActor actor = currentActorProvider.getCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()) || !"AIRLINE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("Only active airline admins can access this endpoint.");

        var scope = aviationServiceClient.getAirlineScope(authorization);
        if (!actor.userId().equals(scope.userId()) || scope.airlineId() == null)
            throw new IllegalEndpoint("The airline scope does not match the authenticated user.");
        return scope.airlineId();
    }

    private List<MaintenanceProviderResponse> providerResponses(List<MaintenanceProvider> providers) {
        List<MaintenanceProviderResponse> response = new ArrayList<>();
        for (MaintenanceProvider provider : providers) {
            response.add(new MaintenanceProviderResponse(provider.getId(), provider.getName(), provider.getContactEmail()));
        }
        return response;
    }

    private List<MaintenanceContractResponse> contractResponses(List<MaintenanceContract> contracts) {
        List<MaintenanceContractResponse> response = new ArrayList<>();
        if (contracts.isEmpty())
            return response;

        List<Long> airlineIds = contracts.stream().map(MaintenanceContract::getAirlineId).distinct().toList();
        List<Long> providerIds = contracts.stream().map(MaintenanceContract::getProviderId).distinct().toList();
        Map<Long, MaintenanceAirlineResponse> airlines = aviationServiceClient.getAirlines(aviationServiceKey, airlineIds).stream()
                .collect(Collectors.toMap(MaintenanceAirlineResponse::id, airline -> airline));
        Map<Long, MaintenanceProvider> providers = maintenanceProviderRepository.findAllById(providerIds).stream()
                .collect(Collectors.toMap(MaintenanceProvider::getId, provider -> provider));
        for (MaintenanceContract contract : contracts) {
            MaintenanceAirlineResponse airline = airlines.get(contract.getAirlineId());
            MaintenanceProvider provider = providers.get(contract.getProviderId());
            response.add(new MaintenanceContractResponse(
                    contract.getId(), contract.getAirlineId(), airline == null ? null : airline.name(),
                    airline == null ? null : airline.airlineCode(), contract.getProviderId(),
                    provider == null ? null : provider.getName(), provider == null ? null : provider.getContactEmail()));
        }
        return response;
    }

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

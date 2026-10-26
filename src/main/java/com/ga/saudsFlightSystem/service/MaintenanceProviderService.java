package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceProviderRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceProviderResponse;
import com.ga.saudsFlightSystem.repository.MaintenanceProviderRepository;
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
    private AuditLogService auditLogService;
    private static final Logger logger = Logger.getLogger(MaintenanceProviderService.class.getName());

    @Transactional
    public ResponseEntity<?> addMaintenanceProvider(AddMaintenanceProviderRequest request) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("faaadmin", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (request.getName() == null || request.getName().isBlank() || request.getContactEmail() == null || request.getContactEmail().isBlank())
            throw new InvalidInformationException("Please fill in all the required fields.");

        if (!EmailService.validateEmailFormat(request.getContactEmail().trim()))
            throw new InvalidInformationException("Pleas put a valid email address.");

        if (request.getName().trim().length() > 255)
            throw new InvalidInformationException("name can't be that long");

        MaintenanceProvider maintenanceProvider = new MaintenanceProvider();
        maintenanceProvider.setName(request.getName().trim());
        maintenanceProvider.setContactEmail(request.getContactEmail().trim());

        maintenanceProviderRepository.save(maintenanceProvider);

        String description = "FAA admin created maintenance provider with id " + maintenanceProvider.getId();

        auditLogService.addAuditLog(user,
                AuditLog.Action.MAINTENANCE_PROVIDER_CREATED,
                AuditLog.EntityType.MAINTENANCE_PROVIDER,
                maintenanceProvider.getId(),
                description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new MaintenanceProviderResponse(maintenanceProvider.getId(), maintenanceProvider.getName(), maintenanceProvider.getContactEmail())
        );
    }

    @Transactional
    public ResponseEntity<?> contractMaintenanceProvider(Long providerId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = user.getAirlineEmployee();

        if (airlineAdmin == null || airlineAdmin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("only airline admins can access this endpoint.");

        MaintenanceProvider maintenanceProvider = maintenanceProviderRepository.findByIdForUpdate(providerId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find Maintenance provider with this id"));

        Airline airline = airlineAdmin.getAirline();
        if (maintenanceProviderRepository.existsByIdAndAirlines_Id(providerId, airline.getId()))
            throw new InformationExistException("Already contracted");

        maintenanceProvider.getAirlines().add(airline);
        maintenanceProviderRepository.save(maintenanceProvider);

        //TODO: send emails to maintenance provider and airline.

        String description = "Airline admin contracted maintenance provider with id " + maintenanceProvider.getId()
                        + " to airline with id " + airline.getId();

        auditLogService.addAuditLog(user,
                AuditLog.Action.MAINTENANCE_PROVIDER_LINKED,
                AuditLog.EntityType.MAINTENANCE_PROVIDER,
                maintenanceProvider.getId(), description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.OK).body(
                new MaintenanceProviderResponse(maintenanceProvider.getId(), maintenanceProvider.getName(), maintenanceProvider.getContactEmail())
        );
    }
}

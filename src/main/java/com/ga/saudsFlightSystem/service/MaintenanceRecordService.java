package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.notification.NotificationPublisher;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.security.CurrentActorProvider;
import com.ga.common.security.CurrentActor;
import com.ga.common.web.exception.IllegalEndpoint;
import com.ga.common.web.exception.BusinessConflictException;
import com.ga.common.web.exception.InformationExistException;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.common.web.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceRecordRequest;
import com.ga.saudsFlightSystem.model.request.CompleteMaintenanceRecordRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceRecordResponse;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceRecordListResponse;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceCancellationNotificationResponse;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceEmployeeRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceProviderRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceContractRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceRecordRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class MaintenanceRecordService {
    private AirlineEmployeeRepository airlineEmployeeRepository;
    private CurrentActorProvider currentActorProvider;
    private MaintenanceRecordRepository maintenanceRecordRepository;
    private MaintenanceEmployeeRepository maintenanceEmployeeRepository;
    private MaintenanceProviderRepository maintenanceProviderRepository;
    private MaintenanceContractRepository maintenanceContractRepository;
    private AirplaneRepository airplaneRepository;
    private AuditLogService auditLogService;
    private AccountLookup accountLookup;
    private NotificationPublisher notificationService;
    private static final Logger logger = Logger.getLogger(MaintenanceRecordService.class.getName());

    @Transactional
    public ResponseEntity<?> addMaintenanceRecord(Long airplaneId, AddMaintenanceRecordRequest request) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"AIRLINE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = airlineEmployeeRepository.findByUserId(actor.userId()).orElse(null);

        if (airlineAdmin == null || airlineAdmin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN || airlineAdmin.getAirline() == null)
            throw new IllegalEndpoint("Only airline admins can request maintenance.");

        if (request == null || request.getProviderId() == null)
            throw new InvalidInformationException("Please fill all the request fields.");

        if (request.getDescription() != null && request.getDescription().trim().length() > 2000)
            throw new InvalidInformationException("Description shouldn't be more than 2000 characters.");

        Airplane airplane = airplaneRepository.findById(airplaneId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find airplane with this id."));

        Long airlineId = airlineAdmin.getAirline().getId();

        if (!Objects.equals(airplane.getAirline().getId(), airlineId))
            throw new IllegalEndpoint("You can only request maintenance for your airline's airplanes.");

        MaintenanceProvider provider = maintenanceProviderRepository.findByIdForUpdate(request.getProviderId())
                        .orElseThrow(() -> new InformationNotFoundException("Couldn't find maintenance provider with this id."));

        if (!maintenanceContractRepository.existsByProviderIdAndAirlineId(provider.getId(), airlineId))
            throw new InvalidInformationException("Your airline is not contracted with this provider.");

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAirplaneId(airplane.getId());
        record.setProvider(provider);

        if (request.getDescription() != null)
            record.setDescription(request.getDescription().trim());

        maintenanceRecordRepository.save(record);

        String description = "Airline admin requested maintenance for airplane with id " + airplaneId
                + " from provider with id " + provider.getId() + " (record id: " + record.getId() + ")";

        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.MAINTENANCE_RECORD_CREATED,
                AuditLog.EntityType.MAINTENANCE_RECORD,
                record.getId(), description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new MaintenanceRecordResponse(
                        record.getId(), airplane.getId(), provider.getId(), null,
                        record.getDescription(), record.getFindings(), record.getStatus(),
                        record.getCreatedAt(), record.getUpdatedAt(), record.getCompletedAt(),
                        record.getCancellationReason(), record.getCancelledAt(),
                        record.getCancelledByUserId()
                )
        );

    }

    @Transactional
    public ResponseEntity<?> getMaintenanceRequests() {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"MAINTENANCE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        MaintenanceEmployee admin = maintenanceEmployeeRepository.findByUserId(actor.userId()).orElse(null);

        if (admin == null || admin.getProvider() == null || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        List<MaintenanceRecord> records = maintenanceRecordRepository.findByProviderIdOrderByCreatedAtDesc(admin.getProvider().getId());

        List<MaintenanceRecordResponse> responses = new ArrayList<>();

        for (MaintenanceRecord record : records) {
            responses.add(new MaintenanceRecordResponse(
                    record.getId(),
                    record.getAirplaneId(),
                    record.getProvider().getId(),
                    record.getTechnician() == null ? null : record.getTechnician().getId(),
                    record.getDescription(),
                    record.getFindings(),
                    record.getStatus(),
                    record.getCreatedAt(),
                    record.getUpdatedAt(),
                    record.getCompletedAt(),
                    record.getCancellationReason(),
                    record.getCancelledAt(),
                    record.getCancelledByUserId()
            ));
        }

        return ResponseEntity.status(HttpStatus.OK).body(responses);
    }

    @Transactional
    public ResponseEntity<?> assignService(Long requestId, Long technicianId) {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"MAINTENANCE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (requestId == null || technicianId == null)
            throw new InvalidInformationException("Please fill the fields");

        MaintenanceEmployee admin = maintenanceEmployeeRepository.findByUserId(actor.userId()).orElse(null);

        if (admin == null || admin.getProvider() == null || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        MaintenanceRecord record = maintenanceRecordRepository.findByIdForUpdate(requestId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find maintenance record with this id"));

        if (!Objects.equals(record.getProvider().getId(), admin.getProvider().getId()))
            throw new IllegalEndpoint("This record does not belong to your organization");

        if (record.getStatus() != MaintenanceRecord.MaintenanceStatus.PENDING)
            throw new BusinessConflictException("Can't assign because this record status is not PENDING.");

        if (record.getTechnician() != null)
            throw new InformationExistException("This request already has an assigned technician.");

        MaintenanceEmployee assignedEmployee = maintenanceEmployeeRepository.findByIdForUpdate(technicianId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find technician with this id"));

        if (assignedEmployee.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.TECHNICIAN)
            throw new InvalidInformationException("Can't assign non-technicians to maintenance jobs");

        if (!Objects.equals(admin.getProvider().getId(), assignedEmployee.getProvider().getId()))
            throw new IllegalEndpoint("You arent allowed to assign employee that aren't from your company.");

        record.setTechnician(assignedEmployee);
        maintenanceRecordRepository.save(record);

        String description = "Maintenance admin assigned technician with id "
                + assignedEmployee.getId()
                + " to maintenance record with id " + record.getId();

        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.MAINTENANCE_TECHNICIAN_ASSIGNED,
                AuditLog.EntityType.MAINTENANCE_RECORD,
                record.getId(),
                description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.ok(new MaintenanceRecordResponse(
                record.getId(),
                record.getAirplaneId(),
                record.getProvider().getId(),
                assignedEmployee.getId(),
                record.getDescription(),
                record.getFindings(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt(), // TODO: test later, could use .saveFlush() or pass LocalDateTime instead.
                record.getCompletedAt(),
                record.getCancellationReason(),
                record.getCancelledAt(),
                record.getCancelledByUserId()
        ));
    }


    @Transactional
    public ResponseEntity<?> getMyMaintenanceAssignments() {
        MaintenanceEmployee technician = getCurrentTechnician();

        List<MaintenanceRecord> records = maintenanceRecordRepository.findByTechnicianIdOrderByCreatedAtDesc(technician.getId());

        List<MaintenanceRecordResponse> responses = new ArrayList<>();

        for (MaintenanceRecord record : records) {
            responses.add(new MaintenanceRecordResponse(
                    record.getId(),
                    record.getAirplaneId(),
                    record.getProvider().getId(),
                    technician.getId(),
                    record.getDescription(),
                    record.getFindings(),
                    record.getStatus(),
                    record.getCreatedAt(),
                    record.getUpdatedAt(),
                    record.getCompletedAt(),
                    record.getCancellationReason(),
                    record.getCancelledAt(),
                    record.getCancelledByUserId()
            ));
        }

        return ResponseEntity.ok(responses);
    }

    @Transactional
    public ResponseEntity<?> completeMaintenance(Long recordId, String findings) {

        MaintenanceEmployee technician = getCurrentTechnician();

        if (findings != null && findings.trim().length() > 2000)
            throw new InvalidInformationException("Findings can't be more than 2000 characters.");

        MaintenanceRecord record = maintenanceRecordRepository.findByIdForUpdate(recordId)
                        .orElseThrow(() -> new InformationNotFoundException("Couldn't find maintenance record with this id."));

        if (record.getTechnician() == null || !Objects.equals(record.getTechnician().getId(), technician.getId()))
            throw new IllegalEndpoint("You can only complete maintenance assigned to you.");

        if (!Objects.equals(record.getProvider().getId(), technician.getProvider().getId()))
            throw new IllegalEndpoint("This record does not belong to your provider.");

        if (record.getStatus() != MaintenanceRecord.MaintenanceStatus.PENDING)
            throw new BusinessConflictException("Only pending maintenance can be done.");

        if (findings != null)
            record.setFindings(findings.trim());
        record.setStatus(MaintenanceRecord.MaintenanceStatus.COMPLETED);
        record.setCompletedAt(LocalDateTime.now());

        maintenanceRecordRepository.save(record);

        CurrentActor actor = currentActorProvider.getCurrentActor();

        String description = "Technician with id " + technician.getId() + " completed maintenance record with id " + recordId;

        auditLogService.addAuditLogForActor(actor,
                AuditLog.Action.MAINTENANCE_RECORD_COMPLETED, AuditLog.EntityType.MAINTENANCE_RECORD, recordId, description
        );

        logger.info(description + " (user id: " + actor.userId() + ")");

        return ResponseEntity.ok("Maintenance completed successfully.");
    }

    @Transactional
    public ResponseEntity<MaintenanceRecordResponse> cancelAirlineMaintenance(Long requestId, String reason) {
        validateRecordId(requestId);
        reason = validateCancellationReason(reason);
        MaintenanceRecord record = maintenanceRecordRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find maintenance record with this id."));
        CurrentActor actor = activeActor();
        AirlineEmployee admin = requireAirlineAdmin(actor);
        Airplane airplane = airplaneRepository.findById(record.getAirplaneId())
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find airplane with this id."));
        if (!Objects.equals(airplane.getAirline().getId(), admin.getAirline().getId()))
            throw new IllegalEndpoint("You can only cancel maintenance for your airline's airplanes.");
        return ResponseEntity.ok(cancelMaintenance(record, actor, reason));
    }

    @Transactional
    public ResponseEntity<MaintenanceRecordResponse> cancelProviderMaintenance(Long requestId, String reason) {
        validateRecordId(requestId);
        reason = validateCancellationReason(reason);
        MaintenanceRecord record = maintenanceRecordRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find maintenance record with this id."));
        CurrentActor actor = activeActor();
        MaintenanceEmployee admin = maintenanceEmployeeRepository.findByUserId(actor.userId()).orElse(null);
        if (!"MAINTENANCE_EMPLOYEE".equals(actor.role()) || admin == null || admin.getProvider() == null
                || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN)
            throw new IllegalEndpoint("Only maintenance admins can cancel their provider's maintenance.");
        if (!Objects.equals(record.getProvider().getId(), admin.getProvider().getId()))
            throw new IllegalEndpoint("This record does not belong to your provider.");
        return ResponseEntity.ok(cancelMaintenance(record, actor, reason));
    }

    @Transactional
    public ResponseEntity<MaintenanceRecordListResponse> getAirlineMaintenanceHistory(Long airplaneId,
            MaintenanceRecord.MaintenanceStatus status, int page, int size) {
        AirlineEmployee admin = requireAirlineAdmin(activeActor());
        if (airplaneId == null || airplaneId <= 0)
            throw new InvalidInformationException("Airplane ID must be positive.");
        if (page < 0 || size < 1 || size > 100)
            throw new InvalidInformationException("Page must be nonnegative and size must be between 1 and 100.");
        Airplane airplane = airplaneRepository.findById(airplaneId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find airplane with this id."));
        Long airlineId = admin.getAirline().getId();
        if (!Objects.equals(airplane.getAirline().getId(), airlineId))
            throw new IllegalEndpoint("You can only view maintenance for your airline's airplanes.");
        List<MaintenanceRecord> records = maintenanceRecordRepository.findAirlineHistory(airplaneId, airlineId,
                status == null ? null : status.name(), size, (long) page * size);
        long totalElements = maintenanceRecordRepository.countAirlineHistory(airplaneId, airlineId,
                status == null ? null : status.name());
        long totalPages = totalElements / size;
        if (totalElements % size != 0)
            totalPages++;
        List<MaintenanceRecordResponse> content = new ArrayList<>();
        for (MaintenanceRecord record : records)
            content.add(toMaintenanceResponse(record));
        return ResponseEntity.ok(new MaintenanceRecordListResponse(content, page, size, totalElements, totalPages));
    }

    // helper methods
    private CurrentActor activeActor() {
        CurrentActor actor = currentActorProvider.getFreshCurrentActor();
        if (!actor.active() || !"ACTIVE".equals(actor.status()))
            throw new IllegalEndpoint("An active account is required.");
        return actor;
    }

    private AirlineEmployee requireAirlineAdmin(CurrentActor actor) {
        AirlineEmployee admin = airlineEmployeeRepository.findByUserId(actor.userId()).orElse(null);
        if (!"AIRLINE_EMPLOYEE".equals(actor.role()) || admin == null || admin.getAirline() == null
                || admin.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN)
            throw new IllegalEndpoint("Only airline admins can access this endpoint.");
        return admin;
    }

    private void validateRecordId(Long requestId) {
        if (requestId == null || requestId <= 0)
            throw new InvalidInformationException("Maintenance record ID must be positive.");
    }

    private String validateCancellationReason(String reason) {
        reason = reason == null ? null : reason.trim();
        if (reason == null || reason.isEmpty() || reason.length() > 2000)
            throw new InvalidInformationException("A cancellation reason between 1 and 2000 characters is required.");
        return reason;
    }

    private MaintenanceRecordResponse cancelMaintenance(MaintenanceRecord record, CurrentActor actor, String reason) {
        if (record.getStatus() != MaintenanceRecord.MaintenanceStatus.PENDING || record.getCompletedAt() != null)
            throw new BusinessConflictException("Only pending maintenance without a completion time can be cancelled. Refresh the record.");
        record.setStatus(MaintenanceRecord.MaintenanceStatus.CANCELLED);
        record.setCancellationReason(reason);
        record.setCancelledAt(Instant.now());

        record.setCancelledByUserId(actor.userId());
        maintenanceRecordRepository.save(record);
        maintenanceRecordRepository.flush();

        String description = "User " + actor.userId() + " cancelled maintenance record " + record.getId();
        auditLogService.addAuditLogForActor(actor, AuditLog.Action.MAINTENANCE_CANCELLED,
                AuditLog.EntityType.MAINTENANCE_RECORD, record.getId(), description);
        logger.info(description);

        MaintenanceCancellationNotificationResponse payload = new MaintenanceCancellationNotificationResponse(
                record.getId(), record.getStatus(), record.getCancelledAt());
        Airplane airplane = airplaneRepository.findById(record.getAirplaneId())
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find airplane with this id."));
        Set<Long> recipients = new LinkedHashSet<>(accountLookup.findActiveUserIds(
                airlineEmployeeRepository.findAdminUserIdsByAirlineId(airplane.getAirline().getId()), "AIRLINE_EMPLOYEE"));
        recipients.addAll(accountLookup.findActiveUserIds(
                maintenanceEmployeeRepository.findCancellationRecipientUserIds(record.getId(), record.getProvider().getId()), "MAINTENANCE_EMPLOYEE"));

        for (Long recipientId : recipients) {
            notificationService.sendToUser(recipientId, "Maintenance_Cancelled", payload);
        }
        return toMaintenanceResponse(record);
    }

    private MaintenanceRecordResponse toMaintenanceResponse(MaintenanceRecord record) {
        return new MaintenanceRecordResponse(record.getId(), record.getAirplaneId(), record.getProvider().getId(),
                record.getTechnician() == null ? null : record.getTechnician().getId(), record.getDescription(), record.getFindings(),
                record.getStatus(), record.getCreatedAt(), record.getUpdatedAt(), record.getCompletedAt(),
                record.getCancellationReason(), record.getCancelledAt(), record.getCancelledByUserId());
    }

    private MaintenanceEmployee getCurrentTechnician() {
        CurrentActor actor = currentActorProvider.getCurrentActor();

        if (!"MAINTENANCE_EMPLOYEE".equals(actor.role()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        MaintenanceEmployee technician = maintenanceEmployeeRepository.findByUserId(actor.userId()).orElse(null);

        if (technician == null || technician.getProvider() == null || technician.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.TECHNICIAN)
            throw new IllegalEndpoint("Only maintenance technicians can access this endpoint.");

        return technician;
    }


}

package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceRecordRequest;
import com.ga.saudsFlightSystem.model.request.CompleteMaintenanceRecordRequest;
import com.ga.saudsFlightSystem.model.request.response.MaintenanceRecordResponse;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceEmployeeRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceProviderRepository;
import com.ga.saudsFlightSystem.repository.MaintenanceRecordRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class MaintenanceRecordService {
    private MaintenanceRecordRepository maintenanceRecordRepository;
    private MaintenanceEmployeeRepository maintenanceEmployeeRepository;
    private MaintenanceProviderRepository maintenanceProviderRepository;
    private AirplaneRepository airplaneRepository;
    private AuditLogService auditLogService;
    private static final Logger logger = Logger.getLogger(MaintenanceRecordService.class.getName());

    @Transactional
    public ResponseEntity<?> addMaintenanceRecord(Long airplaneId, AddMaintenanceRecordRequest request) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        AirlineEmployee airlineAdmin = user.getAirlineEmployee();

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

        if (!maintenanceProviderRepository.existsByIdAndAirlines_Id(provider.getId(), airlineId))
            throw new InvalidInformationException("Your airline is not contracted with this provider.");

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAirplane(airplane);
        record.setProvider(provider);

        if (request.getDescription() != null)
            record.setDescription(request.getDescription().trim());

        maintenanceRecordRepository.save(record);

        String description = "Airline admin requested maintenance for airplane with id " + airplaneId
                + " from provider with id " + provider.getId() + " (record id: " + record.getId() + ")";

        auditLogService.addAuditLog(user,
                AuditLog.Action.MAINTENANCE_RECORD_CREATED,
                AuditLog.EntityType.MAINTENANCE_RECORD,
                record.getId(), description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new MaintenanceRecordResponse(
                        record.getId(), airplane.getId(), provider.getId(), null,
                        record.getDescription(), record.getFindings(), record.getStatus(),
                        record.getCreatedAt(), record.getUpdatedAt(), record.getCompletedAt()
                )
        );

    }

    @Transactional
    public ResponseEntity<?> getMaintenanceRequests() {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("maintenanceEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        MaintenanceEmployee admin = user.getMaintenanceEmployee();

        if (admin == null || admin.getProvider() == null || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        List<MaintenanceRecord> records = maintenanceRecordRepository.findByProviderIdOrderByCreatedAtDesc(admin.getProvider().getId());

        List<MaintenanceRecordResponse> responses = new ArrayList<>();

        for (MaintenanceRecord record : records) {
            responses.add(new MaintenanceRecordResponse(
                    record.getId(),
                    record.getAirplane().getId(),
                    record.getProvider().getId(),
                    record.getTechnician() == null ? null : record.getTechnician().getId(),
                    record.getDescription(),
                    record.getFindings(),
                    record.getStatus(),
                    record.getCreatedAt(),
                    record.getUpdatedAt(),
                    record.getCompletedAt()
            ));
        }

        return ResponseEntity.status(HttpStatus.OK).body(responses);
    }

    @Transactional
    public ResponseEntity<?> assignService(Long requestId, Long technicianId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("maintenanceEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (requestId == null || technicianId == null)
            throw new InvalidInformationException("Please fill the fields");

        MaintenanceEmployee admin = user.getMaintenanceEmployee();

        if (admin == null || admin.getProvider() == null || admin.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.ADMIN)
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        MaintenanceRecord record = maintenanceRecordRepository.findByIdForUpdate(requestId)
                .orElseThrow( () -> new InformationNotFoundException("Couldn't find maintenance record with this id"));

        if (!Objects.equals(record.getProvider().getId(), admin.getProvider().getId()))
            throw new IllegalEndpoint("This record does not belong to your organization");

        if (record.getStatus() != MaintenanceRecord.MaintenanceStatus.PENDING)
            throw new InvalidInformationException("Can't assign because this record status is not PENDING.");

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

        auditLogService.addAuditLog(
                user,
                AuditLog.Action.MAINTENANCE_TECHNICIAN_ASSIGNED,
                AuditLog.EntityType.MAINTENANCE_RECORD,
                record.getId(),
                description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.ok(new MaintenanceRecordResponse(
                record.getId(),
                record.getAirplane().getId(),
                record.getProvider().getId(),
                assignedEmployee.getId(),
                record.getDescription(),
                record.getFindings(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt(), // TODO: test later, could use .saveFlush() or pass LocalDateTime instead.
                record.getCompletedAt()
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
                    record.getAirplane().getId(),
                    record.getProvider().getId(),
                    technician.getId(),
                    record.getDescription(),
                    record.getFindings(),
                    record.getStatus(),
                    record.getCreatedAt(),
                    record.getUpdatedAt(),
                    record.getCompletedAt()
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
            throw new InvalidInformationException("Only pending maintenance can be done.");

        if (findings != null)
            record.setFindings(findings.trim());
        record.setStatus(MaintenanceRecord.MaintenanceStatus.COMPLETED);
        record.setCompletedAt(LocalDateTime.now());

        maintenanceRecordRepository.save(record);

        User user = UserService.getCurrentLoggedInUser();

        String description = "Technician with id " + technician.getId() + " completed maintenance record with id " + recordId;

        auditLogService.addAuditLog(user,
                AuditLog.Action.MAINTENANCE_RECORD_COMPLETED, AuditLog.EntityType.MAINTENANCE_RECORD, recordId, description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.ok("Maintenance completed successfully.");
    }

    private MaintenanceEmployee getCurrentTechnician() {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("maintenanceEmployee", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        MaintenanceEmployee technician = user.getMaintenanceEmployee();

        if (technician == null || technician.getProvider() == null || technician.getMaintenanceRole() != MaintenanceEmployee.MaintenanceRole.TECHNICIAN)
            throw new IllegalEndpoint("Only maintenance technicians can access this endpoint.");

        return technician;
    }


}

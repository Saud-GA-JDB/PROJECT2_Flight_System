package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.AddMaintenanceRecordRequest;
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
}

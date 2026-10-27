package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airport;
import com.ga.saudsFlightSystem.model.AirportOperator;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AddAirportOperatorRequest;
import com.ga.saudsFlightSystem.model.request.response.AirportOperatorResponse;
import com.ga.saudsFlightSystem.repository.AirportEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirportOperatorRepository;
import com.ga.saudsFlightSystem.repository.AirportRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class AirportOperatorService {
    private AirportOperatorRepository airportOperatorRepository;
    private AuditLogService auditLogService;
    private AirportRepository airportRepository;
    private AirportEmployeeRepository airportEmployeeRepository;
    private static final Logger logger = Logger.getLogger(AirportOperatorService.class.getName());

    @Transactional
    public ResponseEntity<?> addAirportOperator(AddAirportOperatorRequest request) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("faaadmin", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        if (request == null || request.getName() == null || request.getName().isBlank()
                || request.getContactEmail() == null || request.getContactEmail().isBlank())
            throw new InvalidInformationException("Please fill in all the required fields.");

        String name = request.getName().trim();
        String contactEmail = request.getContactEmail().trim();

        if (name.length() > 255 || contactEmail.length() > 255)
            throw new InvalidInformationException("Fields must be less than 255 characters.");

        if (!EmailService.validateEmailFormat(contactEmail))
            throw new InvalidInformationException("Please enter a valid email address.");

        AirportOperator operator = new AirportOperator();
        operator.setName(name);
        operator.setContactEmail(contactEmail);

        airportOperatorRepository.save(operator);

        String description = "FAA admin created airport operator with id " + operator.getId();

        auditLogService.addAuditLog(user,
                AuditLog.Action.AIRPORT_OPERATOR_CREATED,
                AuditLog.EntityType.AIRPORT_OPERATOR,
                operator.getId(), description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AirportOperatorResponse(operator.getId(), operator.getName(), operator.getContactEmail())
        );
    }

    @Transactional
    public ResponseEntity<?> assignAirportOperator(Long airportId, Long operatorId) {
        User user = UserService.getCurrentLoggedInUser();

        if (!UserService.isAllowedEndpoint("faaadmin", user.getRole()))
            throw new IllegalEndpoint("You are not allowed this endpoint!");

        Airport airport = airportRepository.findByIdForUpdate(airportId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an airport with this Id."));

        if (airport.getOperator() != null && airport.getOperator().getId().equals(operatorId))
            throw new InvalidInformationException("airport is already linked to this operator");

        if (airportEmployeeRepository.existsByAirport_Id(airportId))
            throw new InvalidInformationException("Cannot change the operator of an airport that has employees.");

        AirportOperator operator = airportOperatorRepository.findById(operatorId)
                .orElseThrow(() -> new InformationNotFoundException("Couldn't find an airport operator with this ID."));

        Long previousOperatorId = airport.getOperator() == null ? null : airport.getOperator().getId();

        airport.setOperator(operator);
        airportRepository.save(airport);

        String description = "FAA admin changed airport " + airportId + " operator from " + previousOperatorId + " to " + operatorId;

        auditLogService.addAuditLog(user,
                AuditLog.Action.AIRPORT_OPERATOR_LINKED,
                AuditLog.EntityType.AIRPORT, airportId, description
        );

        logger.info(description + " (user id: " + user.getId() + ")");

        return ResponseEntity.ok("Airport linked to operator successfully.");
    }

}

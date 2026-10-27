package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.AirportOperator;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AddAirportOperatorRequest;
import com.ga.saudsFlightSystem.model.request.response.AirportOperatorResponse;
import com.ga.saudsFlightSystem.repository.AirportOperatorRepository;
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
            throw new InvalidInformationException("Fields must be larger than 255 characters.");

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

}

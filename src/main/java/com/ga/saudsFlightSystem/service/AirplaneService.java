package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.AuditLog;
import com.ga.saudsFlightSystem.model.Flight;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AirplaneRequest;
import com.ga.saudsFlightSystem.model.request.response.AirplaneRequestResponse;
import com.ga.saudsFlightSystem.model.request.response.AddAirplaneResponse;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import com.ga.saudsFlightSystem.repository.AirplaneRequestRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.logging.Logger;

@Service
@AllArgsConstructor
public class AirplaneService {
    private AirplaneRepository airplaneRepository;
    private AirplaneRequestRepository airplaneRequestRepository;
    private NotificationService notificationService;
    private EmailService emailService;
    private AuditLogService auditLogService;

    private static final Logger logger = Logger.getLogger(AirplaneService.class.getName());

    /**
     * Sends an airplane activation request for FAA review after checking ownership
     * and the seven day waiting period between requests.
     *
     * @param airplaneId airplane to request activation for
     * @return the submitted request details
     */
    @Transactional
    public ResponseEntity<?> requestActivation(Long airplaneId) {
        User user = UserService.getCurrentLoggedInUser();
        if (!UserService.isAllowedEndpoint("airlineEmployee", user.getRole())) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        AirlineEmployee airlineEmployee = user.getAirlineEmployee();
        if (airlineEmployee == null || airlineEmployee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN) {
            throw new IllegalEndpoint("Only airline admins can request airplane activation!");
        }
        Airplane airplane = airplaneRepository.findById(airplaneId).orElseThrow(() -> new InformationNotFoundException("couldn't find airplane with this id"));
        if (!Objects.equals(airplane.getAirline().getId(), airlineEmployee.getAirline().getId())) {
            throw new InvalidInformationException("airplane must be owned by the airline");
        }
        if (airplane.getStatus() == Airplane.Status.ACTIVE) {
            throw new InvalidInformationException("The airplane is already active");
        }
        LocalDateTime now = LocalDateTime.now();
        for (AirplaneRequest request : airplaneRequestRepository.findByAirplane(airplane)) {
            if (request.getStatus() == AirplaneRequest.ApprovalStatus.PENDING) {
                throw new InvalidInformationException("The airplane already has a pending activation request");
            }
            if (request.getRequestedAt().plusDays(7).isAfter(now)) {
                throw new InvalidInformationException("You must wait 7 days after the last activation request before requesting again");
            }
        }
        AirplaneRequest request = new AirplaneRequest();
        request.setAirplane(airplane);
        request.setRequestedBy(user);
        airplaneRequestRepository.save(request);

        String description = "Airline admin requested activation of airplane with id " + airplane.getId() + " (request id: " + request.getId() + ")";
        auditLogService.addAuditLog(user, AuditLog.Action.AIRPLANE_ACTIVATION_REQUESTED, AuditLog.EntityType.AIRPLANE_REQUEST, request.getId(), description);

        emailService.sendEmail(user.getEmailAddress(), "Activation Request Submitted", String.format(
                """
                        Your airplane activation request has been submitted for FAA review.
                        Registration number: %s
                        Model: %s
                        Best Regards,
                        Saud Flight System.""",
                airplane.getRegistrationNumber(),
                airplane.getModel()
        ));

        AirplaneRequestResponse response = new AirplaneRequestResponse(
                request.getId(), airplane.getId(), airplane.getRegistrationNumber(), airplane.getModel(),
                request.getStatus(), request.getReviewReason(), request.getRequestedAt(), request.getReviewedAt());
        notificationService.sendToRole(User.Role.FAA_ADMIN, "airplane-activation-requested", response);
        logger.info(description + " (user id: " + user.getId() + ")");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public void checkAvailability(Airplane airplane, LocalDateTime scheduledDeparture, LocalDateTime scheduledArrival) {
        if (airplane.getStatus() == Airplane.Status.GROUNDED) {
            throw new InvalidInformationException("Can't add a flight because the airplane is grounded");
        }

        // leave two hours between flights, whether the new flight is before or after an existing one
        for (Flight flight : airplane.getFlightsList()) {
            if (flight.getStatus() != Flight.FlightStatus.CANCELLED &&
                    scheduledDeparture.isBefore(flight.getScheduledArrival().plusMinutes(120)) &&
                    scheduledArrival.plusMinutes(120).isAfter(flight.getScheduledDeparture())) {
                throw new InvalidInformationException("The airplane must have at least 2 hours between flights");
            }
        }
    }

    /**
     * Adds an airplane to the admin's airline after checking its details.
     *
     * @param registrationNumber unique airplane registration number
     * @param model airplane model
     * @param standardSeatCapacity number of standard seats
     * @param firstClassSeatCapacity number of first class seats
     * @param maxMileage maximum airplane mileage
     * @return the added airplane details or a validation message
     */
    @Transactional
    public ResponseEntity<?> addAirplane(String registrationNumber, String model, int standardSeatCapacity, int firstClassSeatCapacity, Long maxMileage) {
        User user = UserService.getCurrentLoggedInUser();
        if(!UserService.isAllowedEndpoint("airlineEmployee", user.getRole()) ) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        AirlineEmployee airlineEmployee = user.getAirlineEmployee();
        if (airlineEmployee == null || airlineEmployee.getAirlineRole() != AirlineEmployee.AirlineRole.ADMIN) {
            throw new IllegalEndpoint("Only airline admins can add airplanes!");
        }
        if (registrationNumber == null || registrationNumber.isBlank() ||
                model == null || model.isBlank() ||
                firstClassSeatCapacity <= 0 || standardSeatCapacity <= 0 ||
                maxMileage == null ||maxMileage <= 0
        ) {
            logger.warning("Airplane creation rejected because the supplied information was invalid");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body("Failed. please check that all properties are entered and correct");
        }

        if (airplaneRepository.existsByRegistrationNumber(registrationNumber)) {
            logger.warning("Airplane creation rejected because the registration number already exists");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Failed. A Plane with the same registration number already exists.");
        }

        Airplane airplane = new Airplane();
        airplane.setAirline(airlineEmployee.getAirline());
        airplane.setRegistrationNumber(registrationNumber);
        airplane.setModel(model);
        airplane.setFirstClassSeatsCapacity(firstClassSeatCapacity);
        airplane.setStandardSeatsCapacity(standardSeatCapacity);
        airplane.setMaxMileage(maxMileage);

        airplaneRepository.save(airplane);

        String description = "Airline admin added airplane with id " + airplane.getId();
        auditLogService.addAuditLog(user, AuditLog.Action.AIRPLANE_CREATED, AuditLog.EntityType.AIRPLANE, airplane.getId(), description);

        emailService.sendEmail(user.getEmailAddress(), "Airplane Added", String.format(
                "Your airplane has been added successfully." +
                        "\nRegistration number: %s" +
                        "\nModel: %s" +
                        "\nBest Regards," +
                        "\nSaud Flight System.",
                airplane.getRegistrationNumber(),
                airplane.getModel()
        ));

        logger.info(description + " (user id: " + user.getId() + ")");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AddAirplaneResponse(registrationNumber, model, firstClassSeatCapacity+standardSeatCapacity, maxMileage, airplane.getAddedAt(), user.getAirlineEmployee().getCpr(), airplane.getStatus()));
    }
}

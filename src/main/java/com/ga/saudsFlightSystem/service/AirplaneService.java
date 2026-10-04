package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.Airplane;
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

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@AllArgsConstructor
public class AirplaneService {
    private AirplaneRepository airplaneRepository;
    private AirplaneRequestRepository airplaneRequestRepository;

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
        airplaneRequestRepository.save(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new AirplaneRequestResponse(
                request.getId(), airplane.getId(), airplane.getRegistrationNumber(), airplane.getModel(),
                request.getStatus(), request.getReviewReason(), request.getRequestedAt(), request.getReviewedAt()));
    }

    public void checkAvailability(Airplane airplane, LocalDateTime scheduledDeparture, LocalDateTime scheduledArrival) {
        if (airplane.getStatus() == Airplane.Status.GROUNDED) {
            throw new InvalidInformationException("Can't add a flight because the airplane is grounded");
        }

        for (Flight flight : airplane.getFlightsList()) {
            if (flight.getStatus() != Flight.FlightStatus.CANCELLED &&
                    scheduledDeparture.isBefore(flight.getScheduledArrival().plusMinutes(120)) &&
                    scheduledArrival.plusMinutes(120).isAfter(flight.getScheduledDeparture())) {
                throw new InvalidInformationException("The airplane must have at least 2 hours between flights");
            }
        }
    }

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
        ) return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body("Failed. please check that all properties are entered and correct");

        if (airplaneRepository.existsByRegistrationNumber(registrationNumber)) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Failed. A Plane with the same registration number already exists.");

        Airplane airplane = new Airplane();
        airplane.setAirline(airlineEmployee.getAirline());
        airplane.setRegistrationNumber(registrationNumber);
        airplane.setModel(model);
        airplane.setFirstClassSeatsCapacity(firstClassSeatCapacity);
        airplane.setStandardSeatsCapacity(standardSeatCapacity);
        airplane.setMaxMileage(maxMileage);

        airplaneRepository.save(airplane);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AddAirplaneResponse(registrationNumber, model, firstClassSeatCapacity+standardSeatCapacity, maxMileage, airplane.getAddedAt(), user.getAirlineEmployee().getCpr(), airplane.getStatus()));
    }
}

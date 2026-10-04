package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.Airplane;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.response.AddAirplaneResponse;
import com.ga.saudsFlightSystem.repository.AirplaneRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class AirplaneService {
    private AirplaneRepository airplaneRepository;

    public ResponseEntity<?> addAirplane(String registrationNumber, String model, int seatCapacity, Long maxMileage) {
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
                seatCapacity <= 0 || maxMileage == null ||maxMileage <= 0
        ) return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body("Failed. please check that all properties are entered and correct");

        if (airplaneRepository.existsByRegistrationNumber(registrationNumber)) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Failed. A Plane with the same registration number already exists.");

        Airplane airplane = new Airplane();
        airplane.setAirline(airlineEmployee.getAirline());
        airplane.setRegistrationNumber(registrationNumber);
        airplane.setModel(model);
        airplane.setSeatCapacity(seatCapacity);
        airplane.setMaxMileage(maxMileage);

        airplaneRepository.save(airplane);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AddAirplaneResponse(registrationNumber, model, seatCapacity, maxMileage, airplane.getAddedAt(), user.getAirlineEmployee().getCpr(), airplane.getStatus()));
    }
}

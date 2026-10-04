package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.AirlineEmployee;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.AddAirlineAdminRequest;
import com.ga.saudsFlightSystem.repository.AirlineEmployeeRepository;
import com.ga.saudsFlightSystem.repository.AirlineRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
@AllArgsConstructor
public class AirlineEmployeeService {
    AirlineRepository airlineRepository;
    UserRepository userRepository;
    AirlineEmployeeRepository airlineEmployeeRepository;
    PasswordEncoder passwordEncoder;
    PhoneValidationService phoneValidationService;
    PendingRegistrationService pendingRegistrationService;
    public ResponseEntity<?> addAirlineAdmin(Long airlineId, AddAirlineAdminRequest request) {
        User user = UserService.getCurrentLoggedInUser();
        if(!UserService.isAllowedEndpoint("faaadmin", UserService.getCurrentLoggedInUser().getRole()) ) {
            throw new IllegalEndpoint("You are not allowed this API Endpoint!");
        }
        //validation
        if (request.getCpr() == null || !request.getCpr().matches("[0-9]{9}")) {
            throw new InvalidInformationException("CPR must contain exactly 9 digits.");
        }
        if (!EmailService.validateEmailFormat(request.getEmailAddress())) {
            throw new InvalidInformationException("invalid Email Format");
        }
        if (request.getFName() == null || request.getFName().isBlank()
                || request.getLName() == null || request.getLName().isBlank()) {
            throw new InvalidInformationException("First name and last name are required.");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()
                || request.getPhoneNumberOpeningCode() == null
                || request.getPhoneNumberOpeningCode().isBlank()) {
            throw new InvalidInformationException("Phone number and opening code are required.");
        }
        if (!phoneValidationService.isValidPhoneNumber(request.getPhoneNumber(), request.getPhoneNumberOpeningCode())) {
            throw new InvalidInformationException("Invalid phone number or country code.");
        }
        if (request.getSalary() == null || request.getSalary() <= 0) {
            throw new InvalidInformationException("Salary must be greater than zero.");
        }
        if (request.getHireDate() == null || request.getHireDate().isBlank()) {
            throw new InvalidInformationException("Hire date is required.");
        }
        LocalDate hireDate;
        try {
            hireDate = LocalDate.parse(request.getHireDate());
        } catch (DateTimeParseException e) {
            throw new InvalidInformationException("Hire date must be a valid date in yyyy-MM-dd format.");
        }
        if (request.getSecurityQuestion() == null || request.getSecurityQuestion().isBlank()
                || request.getSecurityQuestionAnswer() == null
                || request.getSecurityQuestionAnswer().isBlank()) {
            throw new InvalidInformationException("Security question and answer are required.");
        }
        pendingRegistrationService.checkExistingDetails(request.getEmailAddress(), request.getCpr());


        AirlineEmployee airlineEmployee = new AirlineEmployee();
        airlineEmployee.setAirline(airlineRepository.findById(airlineId).orElseThrow(()-> new InformationNotFoundException("couldn't find the airline with this id")));
        airlineEmployee.setAirlineRole(AirlineEmployee.AirlineRole.ADMIN);
        airlineEmployee.setSalary(request.getSalary());
        airlineEmployee.setCpr(request.getCpr());
        airlineEmployee.setFName(request.getFName());
        airlineEmployee.setLName(request.getLName());
        airlineEmployee.setHireDate(hireDate);
        airlineEmployee.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode());
        airlineEmployee.setPhoneNumber(request.getPhoneNumber());
        User newUser = new User();
        newUser.setPassword(passwordEncoder.encode(request.getCpr()));
        newUser.setRole(User.Role.AIRLINE_EMPLOYEE);
        newUser.setEmailAddress(request.getEmailAddress());
        newUser.setSecurityQuestion(request.getSecurityQuestion());
        newUser.setSecurityQuestionAnswer(passwordEncoder.encode(request.getSecurityQuestionAnswer().trim().toLowerCase()));
        newUser.setAirlineEmployee(airlineEmployee);
//        User.setStatus(User.Status.ACTIVE);
        newUser.setActive(true);
        userRepository.save(newUser);
        newUser.setStatus(User.Status.ACTIVE);
        airlineEmployeeRepository.save(airlineEmployee);
        //TODO: can i return the request as a response since i need the same fields and set securityQuestionAnswer to null??
        request.setSecurityQuestionAnswer(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }
}

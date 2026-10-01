package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Customer;
import com.ga.saudsFlightSystem.model.PendingRegistration;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.LoginRequest;
import com.ga.saudsFlightSystem.model.request.RegistrationRequest;
import com.ga.saudsFlightSystem.model.request.response.LoginResponse;
import com.ga.saudsFlightSystem.repository.PendingRegistrationRepository;
import com.ga.saudsFlightSystem.repository.UserRepository;
import com.ga.saudsFlightSystem.security.JWTUtils;
import com.ga.saudsFlightSystem.security.MyUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final PendingRegistrationService pendingRegistrationService;
    private final PendingRegistrationRepository pendingRegistrationRepository;

    @Autowired
    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder,
                       JWTUtils jwtUtils, @Lazy AuthenticationManager authenticationManager,
                       PendingRegistrationService pendingRegistrationService,
                       PendingRegistrationRepository pendingRegistrationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.pendingRegistrationService = pendingRegistrationService;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
    }

    public User createUser(RegistrationRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidInformationException("Password is required.");
        }
        if (request.getFName() == null || request.getFName().isBlank()
                || request.getLName() == null || request.getLName().isBlank()) {
            throw new InvalidInformationException("First name and last name are required.");
        }
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()
                || request.getPhoneNumberOpeningCode() == null
                || request.getPhoneNumberOpeningCode().isBlank()) {
            throw new InvalidInformationException(
                    "Phone number and opening code are required.");
        }
        if (request.getSecurityQuestion() == null
                || request.getSecurityQuestion().isBlank()
                || request.getSecurityQuestionAnswer() == null
                || request.getSecurityQuestionAnswer().isBlank()) {
            throw new InvalidInformationException(
                    "Security question and answer are required.");
        }

        PendingRegistration pending = pendingRegistrationService.getVerifiedRegistration(
                request.getPendingRegistrationId(), request.getCode());
        pendingRegistrationService.checkExistingDetails(pending.getEmailAddress(), pending.getCpr());

        Customer customer = new Customer();
        customer.setCpr(pending.getCpr());
        customer.setFName(request.getFName());
        customer.setLName(request.getLName());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setPhoneNumberOpeningCode(request.getPhoneNumberOpeningCode());

        User user = new User();
        user.setEmailAddress(pending.getEmailAddress());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setSecurityQuestion(request.getSecurityQuestion());
        user.setSecurityQuestionAnswer(request.getSecurityQuestionAnswer());
        user.setRole(User.Role.CUSTOMER);
        user.setActive(true);
        user.setCustomer(customer);

        User savedUser = userRepository.save(user);
        // Only delete pending after the user saves successfully.
        pendingRegistrationRepository.delete(pending);
        return savedUser;
    }

    public User findUserByEmailAddress(String email) {
        return userRepository.findUserByEmailAddress(email);
    }

    public ResponseEntity<?> loginUser(LoginRequest loginRequest) {
        System.out.println("called login service"); /*         TODO: DEBUGGING         */
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MyUserDetails myUserDetails = (MyUserDetails) authentication.getPrincipal();
            String jwt = jwtUtils.generateJwtToken(myUserDetails);
            return ResponseEntity.ok(new LoginResponse(jwt));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("Error: Email or password is incorrect, or the account is inactive."));
        }
    }

    /*
    Helper Methods
     */

    public static boolean isAllowedEndpoint(String endpoint, User.Role role) {
        switch (role) {
            case CUSTOMER:
                if (endpoint.trim().equalsIgnoreCase("customer")) return true;
                break;
            case FAA_ADMIN:
                if (endpoint.trim().equalsIgnoreCase("faaadmin")) return true;
                break;
            case AIRPORT_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("airportEmployee")) return true;
                break;
            case AIRLINE_EMPLOYEE:
                if (endpoint.trim().equalsIgnoreCase("airlineEmployee")) return true;
                break;
            default:
                throw new InvalidInformationException("Role doesn't exits");
        }
        return false;
    }

    public static User getCurrentLoggedInUser() {
        MyUserDetails userDetails = (MyUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userDetails.getUser();
    }
}

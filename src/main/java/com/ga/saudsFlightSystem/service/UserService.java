package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.InformationExistException;
import com.ga.saudsFlightSystem.exception.InvalidInformationException;
import com.ga.saudsFlightSystem.model.Person;
import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.model.request.LoginRequest;
import com.ga.saudsFlightSystem.model.request.response.LoginResponse;
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

    @Autowired
    public UserService(UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder,
                       JWTUtils jwtUtils, @Lazy AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
    }

    public User createUser(User userObject) {
        if (userObject.getEmailAddress() == null || userObject.getEmailAddress().isBlank()
                || userObject.getPassword() == null || userObject.getPassword().isBlank()) {
            throw new InvalidInformationException("Email address and password are required.");
        }

        int profileCount = 0;
        Person profile = null;
        User.Role role = null;
        if (userObject.getCustomer() != null) {
            profileCount++;
            profile = userObject.getCustomer();
            role = User.Role.CUSTOMER;
        }
        if (userObject.getAirlineEmployee() != null) {
            profileCount++;
            profile = userObject.getAirlineEmployee();
            role = User.Role.AIRLINE_EMPLOYEE;
        }
        if (userObject.getFaaAdmin() != null) {
            profileCount++;
            profile = userObject.getFaaAdmin();
            role = User.Role.FAA_ADMIN;
        }
        if (profileCount != 1) {
            throw new InvalidInformationException("Provide exactly one customer, airlineEmployee, or faaAdmin profile.");
        }
        if (userObject.getId() != null || profile.getId() != null) {
            throw new InvalidInformationException("Do not provide IDs when registering a new user and profile.");
        }
        if (userObject.getRole() != null && userObject.getRole() != role) {
            throw new InvalidInformationException("The role must match the profile type.");
        }
        if (userRepository.existsByEmailAddress(userObject.getEmailAddress())) {
            throw new InformationExistException("User with this email address already exists.");
        }

        userObject.setRole(role);
        userObject.setActive(true);
        userObject.setPassword(passwordEncoder.encode(userObject.getPassword()));
        return userRepository.save(userObject);
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

    public boolean isAllowedEndpoint(String endpoint, User.Role role) {
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
}

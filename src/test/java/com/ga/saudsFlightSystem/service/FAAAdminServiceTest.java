package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.*;
import com.ga.saudsFlightSystem.repository.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class FAAAdminServiceTest {
    @Autowired
    FAAAdminService faaAdminService;
    @Autowired
    AirlineService airlineService;
    @Autowired
    AirlineRepository airlineRepository;
    @Autowired
    AirplaneRequestRepository airplaneRequestRepository;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JsonMapper jsonMapper;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.faa@mailsac.com", "TestPassword1")));
        Assert.assertEquals(User.Role.FAA_ADMIN, UserService.getCurrentLoggedInUser().getRole());
        Assert.assertNotNull(UserService.getCurrentLoggedInUser().getFaaAdmin());
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("when FAA admin adds airline then airline is saved")
    public final void whenFAAAdminAddsAirlineThenAirlineIsSaved() {
        Airline airline = airlineService.addAirline("Test Created Airline", "TCA", "Bahrain");

        Assert.assertNotNull(airline.getId());
        Assert.assertEquals("Test Created Airline", airline.getName());
        Assert.assertEquals(airline.getId(), airlineRepository.findByAirlineCode("TCA").orElseThrow().getId());
    }

    @Test
    @DisplayName("when FAA admin approves request then airplane is active")
    public final void whenFAAAdminApprovesRequestThenAirplaneIsActive() {
        AirplaneRequest request = airplaneRequestRepository.findById(900002L).orElse(null);
        Assert.assertNotNull("Add the activation request from TESTING-NOTES-TEMP.md", request);
        Assert.assertEquals(AirplaneRequest.ApprovalStatus.PENDING, request.getStatus());
        Assert.assertEquals(Airplane.Status.GROUNDED, request.getAirplane().getStatus());
        Assert.assertEquals("flighttest.airline@mailsac.com", request.getRequestedBy().getEmailAddress());
        ReviewAirplaneRequest review = jsonMapper.readValue(
                "{\"status\":\"ACCEPTED\",\"reviewReason\":\"Test approval\"}", ReviewAirplaneRequest.class);

        ResponseEntity<?> response = faaAdminService.reviewAirplaneRequest(request.getId(), review);

        Assert.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assert.assertEquals(AirplaneRequest.ApprovalStatus.ACCEPTED, request.getStatus());
        Assert.assertEquals(Airplane.Status.ACTIVE, request.getAirplane().getStatus());
        Assert.assertEquals(UserService.getCurrentLoggedInUser().getFaaAdmin().getId(), request.getReviewedBy().getId());
        Assert.assertNotNull(request.getReviewedAt());
    }

    @Test(expected = IllegalEndpoint.class)
    @DisplayName("when airline admin approves activation then access is denied")
    public final void whenAirlineAdminApprovesActivationThenAccessIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.airline@mailsac.com", "TestPassword1")));
        Assert.assertEquals(User.Role.AIRLINE_EMPLOYEE, UserService.getCurrentLoggedInUser().getRole());
        Assert.assertEquals(AirlineEmployee.AirlineRole.ADMIN,
                UserService.getCurrentLoggedInUser().getAirlineEmployee().getAirlineRole());
        ReviewAirplaneRequest review = jsonMapper.readValue(
                "{\"status\":\"ACCEPTED\",\"reviewReason\":\"Test approval\"}", ReviewAirplaneRequest.class);

        faaAdminService.reviewAirplaneRequest(900002L, review);
    }
}

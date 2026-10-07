package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.exception.IllegalEndpoint;
import com.ga.saudsFlightSystem.model.*;
import com.ga.saudsFlightSystem.model.request.*;
import com.ga.saudsFlightSystem.model.request.response.AddFlightResponse;
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

import java.time.LocalDateTime;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class AirlineEmployeeServiceTest {
    @Autowired
    AirlineEmployeeService airlineEmployeeService;
    @Autowired
    AirplaneService airplaneService;
    @Autowired
    AirplaneRepository airplaneRepository;
    @Autowired
    FlightRepository flightRepository;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JsonMapper jsonMapper;

    @Before
    public void setUp() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.airline@mailsac.com", "TestPassword1")));
        Assert.assertEquals(User.Role.AIRLINE_EMPLOYEE, UserService.getCurrentLoggedInUser().getRole());
        Assert.assertEquals(AirlineEmployee.AirlineRole.ADMIN,
                UserService.getCurrentLoggedInUser().getAirlineEmployee().getAirlineRole());
    }

    @After
    public void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("when airline admin adds airplane then airplane is saved")
    public final void whenAirlineAdminAddsAirplaneThenAirplaneIsSaved() {
        long airplaneCount = airplaneRepository.count();

        ResponseEntity<?> response = airplaneService.addAirplane("TEST-NEW-PLANE", "Test Model", 100, 10, 5000L);

        Assert.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assert.assertEquals(airplaneCount + 1, airplaneRepository.count());
        Assert.assertTrue(airplaneRepository.existsByRegistrationNumber("TEST-NEW-PLANE"));
    }

    @Test
    @DisplayName("when airline admin adds flight then flight is saved")
    public final void whenAirlineAdminAddsFlightThenFlightIsSaved() {
        Airplane airplane = airplaneRepository.findById(900003L).orElse(null);
        Assert.assertNotNull("Add the free airplane from TESTING-NOTES-TEMP.md", airplane);
        Assert.assertEquals(Airplane.Status.ACTIVE, airplane.getStatus());
        Assert.assertEquals(UserService.getCurrentLoggedInUser().getAirlineEmployee().getAirline().getId(),
                airplane.getAirline().getId());
        Assert.assertTrue(airplane.getFlightsList().isEmpty());
        LocalDateTime departure = LocalDateTime.now().plusDays(7).withNano(0);
        LocalDateTime arrival = departure.plusHours(2);
        AddFlightRequest request = jsonMapper.readValue(
                "{\"originAirportIataCode\":\"BAH\",\"arrivalAirportIataCode\":\"DXB\","
                        + "\"scheduledDeparture\":\"" + departure + "\",\"scheduledArrival\":\"" + arrival + "\"}",
                AddFlightRequest.class);
        long flightCount = flightRepository.count();

        ResponseEntity<?> response = airlineEmployeeService.addFlight(airplane.getId(), request);
        AddFlightResponse flight = (AddFlightResponse) response.getBody();

        Assert.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assert.assertEquals(flightCount + 1, flightRepository.count());
        Assert.assertNotNull(flight);
        Assert.assertEquals(Flight.FlightStatus.ACTIVE, flight.getStatus());
        Assert.assertEquals(airplane.getRegistrationNumber(), flight.getAirplaneRegistrationNumber());
        Assert.assertEquals("BAH", flight.getOriginAirportIataCode());
        Assert.assertEquals("DXB", flight.getArrivalAirportIataCode());
        Assert.assertEquals(departure, flight.getScheduledDeparture());
        Assert.assertEquals(arrival, flight.getScheduledArrival());
    }

    @Test(expected = IllegalEndpoint.class)
    @DisplayName("when customer adds flight then access is denied")
    public final void whenCustomerAddsFlightThenAccessIsDenied() {
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("flighttest.customer@mailsac.com", "TestPassword1")));
        Assert.assertEquals(User.Role.CUSTOMER, UserService.getCurrentLoggedInUser().getRole());

        airlineEmployeeService.addFlight(900003L, new AddFlightRequest());
    }
}

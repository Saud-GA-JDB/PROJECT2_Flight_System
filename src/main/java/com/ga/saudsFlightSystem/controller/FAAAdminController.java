package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.Email;
import com.ga.saudsFlightSystem.model.request.AddAirlineAdminRequest;
import com.ga.saudsFlightSystem.model.request.ReviewAirplaneRequest;
import com.ga.saudsFlightSystem.service.*;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/faaadmin")
@AllArgsConstructor
public class FAAAdminController {
    private FAAAdminService faaAdminService;
    private UserService userService;
    private AirlineService airlineService;
    private EmailService emailService;
    private AirlineEmployeeService airlineEmployeeService;

    @GetMapping("/airplaneRequests")
    public ResponseEntity<?> getPendingAirplaneRequests() {
        return faaAdminService.getPendingAirplaneRequests();
    }

    @PutMapping("/airplaneRequests/{requestId}")
    public ResponseEntity<?> reviewAirplaneRequest(@PathVariable(name = "requestId") Long requestId, @RequestBody ReviewAirplaneRequest review) {
        return faaAdminService.reviewAirplaneRequest(requestId, review);
    }

    @GetMapping
    public String test() {
        System.out.println("test ran");
        emailService.sendEmail("saud11alkh@gmail.com", "Ticket", "Hello Saud Enjoy");
        return "hello test";
    }

    @PostMapping("/airlines")
    public Airline createAirline(String name, String airlineCode, String headquartersCountry) {
        return airlineService.addAirline(name, airlineCode, headquartersCountry);
    }

    @GetMapping("/airlines")
    public List<Airline> getAllAirlines() {
        return airlineService.getAllAirlines();
    }

    @PostMapping("/airlines/{airlineId}/addAirlineAdmin")
    public ResponseEntity<?> addAirlineAdmin(@PathVariable(name = "airlineId") Long airlineId, @RequestBody AddAirlineAdminRequest request) {
        return airlineEmployeeService.addAirlineAdmin(airlineId, request);
    }


}

package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Airline;
import com.ga.saudsFlightSystem.model.Email;
import com.ga.saudsFlightSystem.service.AirlineService;
import com.ga.saudsFlightSystem.service.EmailService;
import com.ga.saudsFlightSystem.service.FAAAdminService;
import com.ga.saudsFlightSystem.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/faaadmin")
@AllArgsConstructor
public class FAAAdminController {
    private FAAAdminService faaAdminService;
    private UserService userService;
    private AirlineService airlineService;
    private EmailService emailService;

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


}

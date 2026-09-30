package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.Airline;
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

    @GetMapping
    public String test() {
        System.out.println("test ran");
        return "hello test";
    }

    @PostMapping("/airlines")
    public Airline createAirline(@RequestBody Airline airline) {
        return airlineService.addAirline(airline);
    }

//    @PostMapping("/login")
}

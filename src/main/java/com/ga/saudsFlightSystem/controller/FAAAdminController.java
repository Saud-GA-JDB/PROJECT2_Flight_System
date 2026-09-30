package com.ga.saudsFlightSystem.controller;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/faaadmin")
@AllArgsConstructor
public class FAAAdminController {
//    private FAAAdminService faaAdminService;

    @GetMapping
    public String test() {
        System.out.println("test ran");
        return "hello test";
    }

//    @PostMapping("/login")
}

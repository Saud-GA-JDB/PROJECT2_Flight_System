package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.CurrentEmployeeResponse;
import com.ga.saudsFlightSystem.service.EmployeeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/employees")
public class EmployeeController {
    private EmployeeService employeeService;

    @GetMapping("/me")
    public CurrentEmployeeResponse getCurrentEmployee() {
        return employeeService.getCurrentEmployee();
    }
}

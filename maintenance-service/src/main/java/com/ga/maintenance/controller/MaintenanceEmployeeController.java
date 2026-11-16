package com.ga.maintenance.controller;

import com.ga.maintenance.model.request.response.CurrentEmployeeResponse;
import com.ga.maintenance.service.MaintenanceEmployeeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/maintenanceEmployees")
public class MaintenanceEmployeeController {
    private MaintenanceEmployeeService maintenanceEmployeeService;

    @GetMapping("/me")
    public CurrentEmployeeResponse getCurrentEmployee() {
        return maintenanceEmployeeService.getCurrentEmployee();
    }
}

package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.AirlineResponse;
import com.ga.saudsFlightSystem.model.request.*;
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
    private AirlineService airlineService;
    private AirlineEmployeeService airlineEmployeeService;
    private MaintenanceProviderService maintenanceProviderService;
    private MaintenanceEmployeeService maintenanceEmployeeService;
    private AirportOperatorService airportOperatorService;
    private AirportEmployeeService airportEmployeeService;

    @PostMapping("/airlines")
    public AirlineResponse createAirline(String name, String airlineCode, String headquartersCountry) {
        return airlineService.addAirline(name, airlineCode, headquartersCountry);
    }

    @GetMapping("/airlines")
    public List<AirlineResponse> getAllAirlines() {
        return airlineService.getAllAirlines();
    }

    @PostMapping("/airlines/{airlineId}/addAirlineAdmin")
    public ResponseEntity<?> addAirlineAdmin(@PathVariable(name = "airlineId") Long airlineId, @RequestBody AddAirlineAdminRequest request) {
        return airlineEmployeeService.addAirlineAdmin(airlineId, request);
    }

    @GetMapping("/airplaneRequests")
    public ResponseEntity<?> getPendingAirplaneRequests() {
        return faaAdminService.getPendingAirplaneRequests();
    }

    @PutMapping("/airplaneRequests/{requestId}")
    public ResponseEntity<?> reviewAirplaneRequest(@PathVariable(name = "requestId") Long requestId, @RequestBody ReviewAirplaneRequest request) {
        return faaAdminService.reviewAirplaneRequest(requestId, request);
    }

    @PostMapping("/maintenanceProviders")
    public ResponseEntity<?> addMaintenanceProvider(@RequestBody AddMaintenanceProviderRequest request) {
        return maintenanceProviderService.addMaintenanceProvider(request);
    }

    @PostMapping("/maintenanceProviders/{providerId}/addMaintenanceAdmin")
    public ResponseEntity<?> addMaintenanceProviderAdmin(@PathVariable(name = "providerId") Long providerId, @RequestBody AddMaintenanceEmployeeRequest request) {
        return maintenanceEmployeeService.addMaintenanceAdmin(providerId, request);
    }

    @PostMapping("/airportOperators")
    public ResponseEntity<?> addAirportOperator(@RequestBody AddAirportOperatorRequest request) {
        return airportOperatorService.addAirportOperator(request);
    }

    @PutMapping("/airports/{airportId}/operator")
    public ResponseEntity<?> assignAirportOperator(@PathVariable(name = "airportId") Long airportId, @RequestParam(name = "operatorId") Long operatorId) {
        return airportOperatorService.assignAirportOperator(airportId, operatorId);
    }

    @PostMapping("/airports/{airportId}/addAirportOperatorAdmin")
    public ResponseEntity<?> addAirportAdmin(@PathVariable(name = "airportId") Long airportId, @RequestBody AddAirportEmployeeRequest request) {
        return airportEmployeeService.addAirportOperatorAdmin(airportId, request);
    }
}

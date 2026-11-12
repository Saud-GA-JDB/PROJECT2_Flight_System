package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.AirlineResponse;
import com.ga.saudsFlightSystem.model.request.response.ATCEmployeeResponse;
import com.ga.saudsFlightSystem.model.request.response.FacilityResponse;
import com.ga.saudsFlightSystem.model.request.response.ProviderListResponse;
import com.ga.saudsFlightSystem.model.request.response.FacilityListResponse;
import com.ga.saudsFlightSystem.model.request.response.ProviderResponse;
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
    private AirportOperatorService airportOperatorService;
    private AirportEmployeeService airportEmployeeService;
    private ATCService atcService;

    @PostMapping("/airNavigationProviders")
    public ResponseEntity<ProviderResponse> addAirNavigationProvider(@RequestBody AddAirNavigationProviderRequest request) {
        return atcService.addProvider(request);
    }

    @GetMapping("/airNavigationProviders")
    public ResponseEntity<ProviderListResponse> getAirNavigationProviders(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return atcService.getProviders(page, size);
    }

    @PostMapping("/airNavigationProviders/{providerId}/controlFacilities")
    public ResponseEntity<FacilityResponse> addControlFacility(@PathVariable Long providerId, @RequestBody AddControlFacilityRequest request) {
        return atcService.addFacility(providerId, request);
    }

    @GetMapping("/airNavigationProviders/{providerId}/controlFacilities")
    public ResponseEntity<FacilityListResponse> getControlFacilities(@PathVariable Long providerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return atcService.getFacilities(providerId, page, size);
    }

    @PostMapping("/controlFacilities/{facilityId}/admins")
    public ResponseEntity<ATCEmployeeResponse> addATCAdmin(@PathVariable Long facilityId, @RequestBody AddATCEmployeeRequest request) {
        return atcService.addAdmin(facilityId, request);
    }

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

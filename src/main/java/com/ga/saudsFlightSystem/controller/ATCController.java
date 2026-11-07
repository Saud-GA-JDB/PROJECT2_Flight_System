package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.response.FacilityResponse;
import com.ga.saudsFlightSystem.service.ATCService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/atc")
public class ATCController {
    private ATCService atcService;

    @GetMapping("/facility")
    public ResponseEntity<FacilityResponse> getOwnFacility() {
        return atcService.getOwnFacility();
    }
}

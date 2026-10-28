package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.FlightInspection;
import com.ga.saudsFlightSystem.service.FlightInspectionService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/airportInspector")
public class AirportInspectorController {

    private FlightInspectionService flightInspectionService;

    @PutMapping("/flights/{flightId}/inspections/{inspectionId}/complete")
    public ResponseEntity<?> completeInspection(@PathVariable(name = "flightId") Long flightId,
            @PathVariable(name = "inspectionId") Long inspectionId,
            @RequestParam(name = "result") FlightInspection.InspectionStatus result,
            @RequestParam(name = "reason", required = false) String reason) {
        return flightInspectionService.completeInspection(flightId, inspectionId, result, reason);
    }

    @GetMapping("/inspections")
    public ResponseEntity<?> getMyInspections() {
        return flightInspectionService.getMyInspections();
    }

}

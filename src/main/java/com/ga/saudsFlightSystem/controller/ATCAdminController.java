package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.model.request.AddATCEmployeeRequest;
import com.ga.saudsFlightSystem.model.request.response.ATCEmployeeResponse;
import com.ga.saudsFlightSystem.model.request.response.PageResponse;
import com.ga.saudsFlightSystem.service.ATCService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/atcAdmin")
public class ATCAdminController {
    private ATCService atcService;

    @PostMapping("/controllers")
    public ResponseEntity<ATCEmployeeResponse> addController(@RequestBody AddATCEmployeeRequest request) {
        return atcService.addController(request);
    }

    @GetMapping("/controllers")
    public ResponseEntity<PageResponse<ATCEmployeeResponse>> getControllers(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return atcService.getControllers(page, size);
    }
}

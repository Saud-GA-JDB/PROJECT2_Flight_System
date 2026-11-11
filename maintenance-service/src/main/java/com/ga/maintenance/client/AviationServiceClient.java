package com.ga.maintenance.client;

import com.ga.maintenance.model.request.response.MaintenanceScopeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "aviation", url = "${aviation.base-url}")
public interface AviationServiceClient {

    @GetMapping("/internal/v1/maintenance-scope")
    MaintenanceScopeResponse getAirlineScope(@RequestHeader("Authorization") String authorization);

    @GetMapping("/internal/v1/maintenance-scope/airplanes/{airplaneId}")
    MaintenanceScopeResponse getAirplaneScope(@RequestHeader("Authorization") String authorization, @PathVariable("airplaneId") Long airplaneId);
}

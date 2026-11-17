package com.ga.maintenance.client;

import com.ga.common.contract.maintenance.MaintenanceAirlineResponse;
import java.util.List;

import com.ga.common.contract.maintenance.MaintenanceCancellationNotificationRequest;
import com.ga.common.contract.maintenance.MaintenanceScopeResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "aviation", url = "${aviation.base-url}", configuration = AviationClientConfiguration.class)
public interface AviationServiceClient {

    @GetMapping("/internal/v1/maintenance-scope")
    MaintenanceScopeResponse getAirlineScope(@RequestHeader("Authorization") String authorization);

    @GetMapping("/internal/v1/maintenance-scope/airplanes/{airplaneId}")
    MaintenanceScopeResponse getAirplaneScope(@RequestHeader("Authorization") String authorization, @PathVariable("airplaneId") Long airplaneId);

    @PostMapping("/internal/v1/maintenance-scope/airlines")
    List<MaintenanceAirlineResponse> getAirlines(@RequestHeader("X-Service-Key") String serviceKey, @RequestBody List<Long> ids);

    @PostMapping("/internal/v1/maintenance-notifications/cancellations")
    void sendCancellation(@RequestHeader("X-Service-Key") String serviceKey, @RequestBody MaintenanceCancellationNotificationRequest request);
}

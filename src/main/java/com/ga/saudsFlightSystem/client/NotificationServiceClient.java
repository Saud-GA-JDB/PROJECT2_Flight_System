package com.ga.saudsFlightSystem.client;

import com.ga.common.contract.notification.EmailRequest;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "notification", url = "${notification.base-url}", primary = false)
public interface NotificationServiceClient {
    @PostMapping("/internal/v1/emails")
    void sendEmail(@RequestHeader("X-Service-Key") String serviceKey, @RequestBody EmailRequest request);

    @PostMapping("/internal/v1/emails/async")
    void sendAsyncEmail(@RequestHeader("X-Service-Key") String serviceKey, @RequestBody EmailRequest request);
}

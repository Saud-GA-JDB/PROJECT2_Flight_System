package com.ga.identity.client;

import com.ga.identity.model.request.EmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "notification", url = "${notification.base-url:http://localhost:8084}", primary = false)
public interface NotificationServiceClient {
    @PostMapping("/internal/v1/emails")
    void sendEmail(@RequestHeader("X-Service-Key") String serviceKey, @RequestBody EmailRequest request);
}

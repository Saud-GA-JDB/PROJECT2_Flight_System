package com.ga.identity;

import com.ga.common.validation.PasswordService;
import com.ga.common.validation.PhoneValidationService;
import org.springframework.context.annotation.Import;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.ga.common.config.SchedulingConfiguration;

@SpringBootApplication
@Import({SchedulingConfiguration.class, PasswordService.class, PhoneValidationService.class})
@org.springframework.cloud.openfeign.EnableFeignClients(clients = com.ga.identity.client.NotificationServiceClient.class)
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}

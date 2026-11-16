package com.ga.maintenance;

import com.ga.common.validation.PasswordService;
import com.ga.common.validation.PhoneValidationService;
import org.springframework.context.annotation.Import;

import com.ga.maintenance.client.IdentityServiceClient;
import com.ga.maintenance.client.AviationServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.ga.common.config.SchedulingConfiguration;

@SpringBootApplication
@Import({SchedulingConfiguration.class, PasswordService.class, PhoneValidationService.class})
@EnableFeignClients(clients = {AviationServiceClient.class, IdentityServiceClient.class})
public class MaintenanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MaintenanceApplication.class, args);
    }
}

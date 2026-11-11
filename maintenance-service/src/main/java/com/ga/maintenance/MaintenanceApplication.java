package com.ga.maintenance;

import com.ga.common.security.client.IdentityServiceClient;
import com.ga.maintenance.client.AviationServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(clients = {AviationServiceClient.class, IdentityServiceClient.class})
public class MaintenanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MaintenanceApplication.class, args);
    }
}

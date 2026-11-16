package com.ga.notification;

import com.ga.common.security.client.IdentityServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.ga.common.config.SchedulingConfiguration;

@Import(SchedulingConfiguration.class)
@SpringBootApplication
@EnableFeignClients(clients = IdentityServiceClient.class)
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}

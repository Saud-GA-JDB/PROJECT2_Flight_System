package com.ga.saudsFlightSystem;

import com.ga.common.validation.PasswordService;
import com.ga.common.validation.PhoneValidationService;
import org.springframework.context.annotation.Import;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.ga.common.config.SchedulingConfiguration;

@org.springframework.cloud.openfeign.EnableFeignClients(
        clients = {com.ga.saudsFlightSystem.identity.IdentityServiceClient.class,
                com.ga.saudsFlightSystem.client.NotificationServiceClient.class})
@SpringBootApplication
@Import({SchedulingConfiguration.class, PasswordService.class, PhoneValidationService.class})
public class SaudsFlightSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SaudsFlightSystemApplication.class, args);
	}

}

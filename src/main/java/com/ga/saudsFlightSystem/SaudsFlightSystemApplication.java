package com.ga.saudsFlightSystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@org.springframework.cloud.openfeign.EnableFeignClients(
        clients = com.ga.saudsFlightSystem.identity.IdentityServiceClient.class)
@SpringBootApplication
@EnableScheduling
public class SaudsFlightSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SaudsFlightSystemApplication.class, args);
	}

}

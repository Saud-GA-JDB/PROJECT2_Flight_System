package com.ga.saudsFlightSystem;


import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"identity.service-key=fixture-key", "identity.base-url=http://127.0.0.1:1", "app.seed.enabled=false"})
class SaudsFlightSystemApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.ga.saudsFlightSystem.identity.IdentityServiceClient identityClient;

	@Test
	void contextLoads() {
	}

}

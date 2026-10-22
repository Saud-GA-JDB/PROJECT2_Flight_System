package com.ga.saudsFlightSystem.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfiguration {
    @Value("${stripe.secret-key}")
    private String secretKey;

    @Bean
    public StripeClient stripeClient() {
        if (!secretKey.startsWith("sk_test_") && !secretKey.startsWith("rk_test_"))
            throw new IllegalStateException("must be sandbox key");
        return new StripeClient(secretKey);
    }
}

package com.ga.saudsFlightSystem.security;

import com.ga.saudsFlightSystem.identity.IdentityServiceClient;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration extends com.ga.common.security.SecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, IdentityServiceClient identity) throws Exception {
        configureAuthentication(http, identity);

        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/payments/success", "/payments/cancel").permitAll()
                // This endpoint checks the Maintenance service key in its controller.
                .requestMatchers(HttpMethod.POST, "/internal/v1/maintenance-notifications/cancellations").permitAll()
                .anyRequest().hasAuthority("ACCOUNT_ACTIVE"));

        return http.build();
    }
}

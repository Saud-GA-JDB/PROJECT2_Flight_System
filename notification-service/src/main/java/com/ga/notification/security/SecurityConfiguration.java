package com.ga.notification.security;

import com.ga.common.security.client.IdentityServiceClient;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration extends com.ga.common.security.SecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, IdentityServiceClient identity) throws Exception {
        configureAuthentication(http, identity);
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ASYNC && request.getServletPath().equals("/notifications")).permitAll()
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                // Each controller checks the service key on its exact delivery endpoints.
                .requestMatchers(HttpMethod.POST, "/internal/v1/notifications", "/internal/v1/emails", "/internal/v1/emails/async").permitAll()
                .requestMatchers(HttpMethod.GET, "/notifications").hasAuthority("ACCOUNT_ACTIVE")
                .anyRequest().denyAll());
        return http.build();
    }
}

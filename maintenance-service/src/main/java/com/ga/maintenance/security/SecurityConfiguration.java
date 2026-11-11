package com.ga.maintenance.security;

import com.ga.common.security.client.IdentityServiceClient;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration extends com.ga.common.security.SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, IdentityServiceClient identity) throws Exception {
        configureAuthentication(http, identity);

        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .anyRequest().hasAuthority("ACCOUNT_ACTIVE"));

        return http.build();
    }
}

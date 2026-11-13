package com.ga.common.security;

import com.ga.common.security.client.IdentityServiceClient;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public abstract class SecurityConfiguration {

    protected void configureAuthentication(HttpSecurity http, IdentityServiceClient identity) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please login with a valid token"))
                        .accessDeniedHandler((request, response, ex) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "You are not allowed this endpoint")));

        http.addFilterBefore(new JwtRequestFilter(identity), UsernamePasswordAuthenticationFilter.class);
    }
}

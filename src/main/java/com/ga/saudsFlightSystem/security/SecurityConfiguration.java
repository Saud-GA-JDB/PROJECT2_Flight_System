package com.ga.saudsFlightSystem.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration // means it contains Beans
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration {

    private MyUserDetailsService myUserDetailsService;

    @Bean
    public JwtRequestFilter authenticationJwtTokenFilter() {
        return new JwtRequestFilter();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf( csrf -> csrf.disable()) //csrf -> cross sight request forgery. disabled bc the request is from the same domain but here since its the same domain we dont need it. if we has frontend and backend in diff domains then this would be true
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests( auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll() // to see the errors
                        // Initial SSE requests still require ACCOUNT_ACTIVE. Allow container completion dispatches.
                        .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ASYNC
                                && request.getServletPath().equals("/notifications")).permitAll()
                        .requestMatchers( // allow these
                                "/auth/users/login",
                                "/auth/users/register/email",
                                "/auth/users/verification"
                        ).permitAll()
                        .requestMatchers("/auth/users/setup").authenticated() // for completing the user account setup
                        .anyRequest().hasAuthority("ACCOUNT_ACTIVE")
                );
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean // check the authentication manager, if valid return else throw an error
    public AuthenticationManager authenticationManager (
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

}

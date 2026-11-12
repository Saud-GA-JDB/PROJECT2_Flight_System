package com.ga.maintenance.client;

import com.ga.common.web.exception.*;
import com.ga.maintenance.exception.AviationUnavailableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.BadCredentialsException;

// Configuration belongs only to the Aviation client.
public class AviationClientConfiguration {
    @Bean
    public Retryer aviationRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder aviationErrors() {
        return (method, response) -> switch (response.status()) {
            case 400 -> new InvalidInformationException("Aviation rejected the request details.");
            case 401 -> new BadCredentialsException("Aviation requires valid authentication.");
            case 403 -> new IllegalEndpoint("Only an admin of the owning airline can perform this operation.");
            case 404 -> new InformationNotFoundException("The requested Aviation resource was not found.");
            case 409 -> new BusinessConflictException("The Aviation resource is in a conflicting state.");
            default -> new AviationUnavailableException();
        };
    }
}

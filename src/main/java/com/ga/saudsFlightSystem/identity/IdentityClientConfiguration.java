package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.exception.*;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.BadCredentialsException;

// Used only by the Identity Feign client.
public class IdentityClientConfiguration {
    @Bean
    public RequestInterceptor serviceCredential(@Value("${identity.service-key}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("IDENTITY_SERVICE_KEY is required");
        return template -> template.header("X-Service-Key", key);
    }

    @Bean
    public Retryer identityRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder identityErrors() {
        return (method, response) -> switch (response.status()) {
            case 400 -> new InvalidInformationException("Identity rejected the account details.");
            case 401 -> new BadCredentialsException("Invalid token or service credential.");
            case 403 -> new IllegalEndpoint("Identity rejected this operation.");
            case 404 -> new InformationNotFoundException("Account not found.");
            case 409 -> new InformationExistException("Account details already exist.");
            default -> new IdentityUnavailableException();
        };
    }
}

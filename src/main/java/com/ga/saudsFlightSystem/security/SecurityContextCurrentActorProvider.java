package com.ga.saudsFlightSystem.security;

import com.ga.saudsFlightSystem.model.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Adapts the existing monolith principal to the account boundary. */
@Component
public class SecurityContextCurrentActorProvider implements CurrentActorProvider {

    @Override
    public CurrentActor getCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof MyUserDetails userDetails)) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated account is required.");
        }

        User user = userDetails.getUser();
        if (user == null || user.getId() == null || user.getRole() == null || user.getStatus() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated account information is missing.");
        }

        return new CurrentActor(user.getId(), user.getRole().name(), user.getStatus().name(), user.isActive());
    }
}

package com.ga.common.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public abstract class CurrentActorProvider {
    public CurrentActor getCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CurrentActor actor))
            throw new AuthenticationCredentialsNotFoundException("An authenticated account is required.");
        return actor;
    }

    // Services recheck Identity at their existing authorization checkpoints.
    public abstract CurrentActor getFreshCurrentActor();
}

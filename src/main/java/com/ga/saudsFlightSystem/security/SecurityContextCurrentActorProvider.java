package com.ga.saudsFlightSystem.security;

import com.ga.common.security.CurrentActor;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.identity.AccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the scalar principal authenticated by Identity. */
@Component
@RequiredArgsConstructor
public class SecurityContextCurrentActorProvider implements CurrentActorProvider {
    private final AccountLookup accountLookup;

    @Override
    public CurrentActor getFreshCurrentActor() {
        CurrentActor principal = getCurrentActor();
        AccountSnapshot account = accountLookup.findAccountStateById(principal.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("The authenticated account no longer exists."));

        return new CurrentActor(account.userId(), account.role(), account.status(), account.active() );
    }

    @Override
    public CurrentActor getCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CurrentActor actor)) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated account is required.");
        }

        return actor;
    }
}

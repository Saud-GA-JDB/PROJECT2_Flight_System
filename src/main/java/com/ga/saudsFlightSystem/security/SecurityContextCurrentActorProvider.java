package com.ga.saudsFlightSystem.security;

import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.identity.AccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Adapts the existing monolith principal to the account boundary. */
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
                || !(authentication.getPrincipal() instanceof MyUserDetails userDetails)) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated account is required.");
        }

        User user = userDetails.getUser();
        if (user == null || user.getId() == null || user.getRole() == null || user.getStatus() == null)
            throw new AuthenticationCredentialsNotFoundException("Authenticated account information is missing.");

        return new CurrentActor(user.getId(), user.getRole().name(), user.getStatus().name(), user.isActive());
    }
}

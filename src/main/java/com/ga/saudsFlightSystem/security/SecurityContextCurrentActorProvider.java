package com.ga.saudsFlightSystem.security;

import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.saudsFlightSystem.identity.AccountLookup;
import com.ga.saudsFlightSystem.identity.AccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Reads the scalar principal authenticated by Identity. */
@Component
@RequiredArgsConstructor
public class SecurityContextCurrentActorProvider extends CurrentActorProvider {
    private final AccountLookup accountLookup;

    @Override
    public CurrentActor getFreshCurrentActor() {
        CurrentActor principal = getCurrentActor();
        AccountSnapshot account = accountLookup.findAccountStateById(principal.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("The authenticated account no longer exists."));

        return new CurrentActor(account.userId(), account.role(), account.status(), account.active() );
    }

}

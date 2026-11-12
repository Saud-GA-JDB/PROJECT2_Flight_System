package com.ga.maintenance.security;

import com.ga.common.security.CurrentActor;
import com.ga.common.security.CurrentActorProvider;
import com.ga.maintenance.client.IdentityServiceClient;
import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.maintenance.model.request.response.AccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

/** Reads the scalar principal authenticated by Identity. */
@Component
@RequiredArgsConstructor
public class SecurityContextCurrentActorProvider extends CurrentActorProvider {
    private final IdentityServiceClient identity;

    @Override
    public CurrentActor getFreshCurrentActor() {
        CurrentActor principal = getCurrentActor();
        try {
            AccountSnapshot account = identity.account(principal.userId());
            return new CurrentActor(account.userId(), account.role(), account.status(), account.active());
        } catch (InformationNotFoundException ex) {
            throw new AuthenticationCredentialsNotFoundException("The authenticated account no longer exists.");
        }
    }
}

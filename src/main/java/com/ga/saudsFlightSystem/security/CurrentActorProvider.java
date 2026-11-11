package com.ga.saudsFlightSystem.security;

import com.ga.common.security.CurrentActor;

public interface CurrentActorProvider {
    CurrentActor getCurrentActor();

    /** Rechecks persisted account state at commands' existing authorization checkpoints. */
    CurrentActor getFreshCurrentActor();
}

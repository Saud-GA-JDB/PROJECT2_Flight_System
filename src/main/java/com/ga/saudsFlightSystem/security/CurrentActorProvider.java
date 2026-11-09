package com.ga.saudsFlightSystem.security;

public interface CurrentActorProvider {
    CurrentActor getCurrentActor();

    /** Rechecks persisted account state at commands' existing authorization checkpoints. */
    CurrentActor getFreshCurrentActor();
}

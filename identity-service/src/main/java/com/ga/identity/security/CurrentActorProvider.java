package com.ga.identity.security;

public interface CurrentActorProvider {
    CurrentActor getCurrentActor();

    /** Rechecks persisted account state at commands' existing authorization checkpoints. */
    CurrentActor getFreshCurrentActor();
}

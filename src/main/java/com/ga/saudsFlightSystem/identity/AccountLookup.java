package com.ga.saudsFlightSystem.identity;

import java.util.Optional;

public interface AccountLookup {
    Optional<String> findEmailAddressById(Long userId);

    // reads the curr. persisted state without caching
    Optional<AccountSnapshot> findAccountStateById(Long userId);
}

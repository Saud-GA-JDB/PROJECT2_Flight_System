package com.ga.saudsFlightSystem.identity;

import java.util.Optional;

public interface AccountLookup {
    Optional<String> findEmailAddressById(Long userId);
}

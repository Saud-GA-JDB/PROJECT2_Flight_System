package com.ga.saudsFlightSystem.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AccountLookup {
    Optional<String> findEmailAddressById(Long userId);

    // reads the curr. persisted state without caching
    Optional<AccountSnapshot> findAccountStateById(Long userId);

    Map<Long, AccountSnapshot> findAccountsByIds(List<Long> userIds);

    default List<Long> findActiveUserIds(List<Long> userIds, String expectedRole) {
        return findAccountsByIds(userIds).values().stream()
                .filter(account -> account.active() && "ACTIVE".equals(account.status()) && expectedRole.equals(account.role()))
                .map(AccountSnapshot::userId)
                .toList();
    }
}

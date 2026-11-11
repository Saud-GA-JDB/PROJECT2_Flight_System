package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.exception.InformationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RemoteAccountLookup implements AccountLookup {
    private final IdentityServiceClient client;

    @Override
    public Optional<String> findEmailAddressById(Long userId) {
        return findAccountStateById(userId).map(AccountSnapshot::emailAddress);
    }

    @Override
    public Optional<AccountSnapshot> findAccountStateById(Long userId) {
        try {
            return Optional.of(client.account(userId));
        } catch (InformationNotFoundException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Map<Long, AccountSnapshot> findAccountsByIds(List<Long> userIds) {
        return userIds.isEmpty() ? Map.of() : client.accounts(userIds);
    }
}

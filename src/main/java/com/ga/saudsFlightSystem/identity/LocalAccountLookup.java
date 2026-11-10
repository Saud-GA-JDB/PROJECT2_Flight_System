package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LocalAccountLookup implements AccountLookup {
    private final UserRepository userRepository;

    @Override
    public Optional<String> findEmailAddressById(Long userId) {
        return userRepository.findEmailAddressById(userId);
    }

    @Override
    public Optional<AccountSnapshot> findAccountStateById(Long userId) {
        return userRepository.findAccountStateById(userId)
                .map(this::snapshot);
    }

    @Override
    public Map<Long, AccountSnapshot> findAccountsByIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAccountStatesByIds(userIds).stream()
                .map(this::snapshot)
                .collect(Collectors.toMap(AccountSnapshot::userId, account -> account));
    }

    private AccountSnapshot snapshot(UserRepository.AccountState account) {
        return new AccountSnapshot(
                account.getId(),
                account.getRole() == null ? null : account.getRole().name(),
                account.getStatus() == null ? null : account.getStatus().name(),
                account.isActive(),
                account.getEmailAddress()
        );
    }
}

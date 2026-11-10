package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
                .map(account -> new AccountSnapshot(
                        account.getId(),
                        account.getRole() == null ? null : account.getRole().name(),
                        account.getStatus() == null ? null : account.getStatus().name(),
                        account.isActive()
                ));
    }
}

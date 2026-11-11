package com.ga.identity.service;

import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.identity.model.User;
import com.ga.identity.model.request.response.AccountSnapshot;
import com.ga.identity.repository.UserRepository;
import com.ga.identity.security.CurrentActor;
import com.ga.identity.security.JWTUtils;
import com.ga.identity.security.MyUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserRepository userRepository;
    private final JWTUtils jwt;

    public Optional<String> findEmailAddressById(Long userId) {
        return userRepository.findEmailAddressById(userId);
    }

    // reads the curr. persisted state without caching
    public Optional<AccountSnapshot> findAccountStateById(Long userId) {
        return userRepository.findAccountStateById(userId)
                .map(this::snapshot);
    }

    public Map<Long, AccountSnapshot> findAccountsByIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAccountStatesByIds(userIds).stream()
                .map(this::snapshot)
                .collect(Collectors.toMap(AccountSnapshot::userId, account -> account));
    }

    public AccountSnapshot findByEmailAddress(String email) {
        User user = userRepository.findUserByEmailAddress(email);
        if (user == null) throw new InformationNotFoundException("Account not found.");
        return new AccountSnapshot(user.getId(), user.getRole().name(), user.getStatus().name(), user.isActive(), user.getEmailAddress());
    }

    public CurrentActor authenticate(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new BadCredentialsException("Invalid token");
            String email = jwt.getUserNameFromJwtToken(authorization.substring(7));
            User user = userRepository.findUserByEmailAddress(email);
            if (user == null) throw new BadCredentialsException("Invalid token");
            MyUserDetails details = new MyUserDetails(user);
            if (!details.isAccountNonLocked() || !details.isEnabled()) throw new BadCredentialsException("Account unavailable");
            return new CurrentActor(user.getId(), user.getRole().name(), user.getStatus().name(), user.isActive());
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Invalid token or account unavailable");
        }
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

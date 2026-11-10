package com.ga.saudsFlightSystem.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RemotePersonalProfileStore implements PersonalProfileStore {
    private final IdentityClient client;

    @Override
    public Optional<PersonalProfile> findByUserId(Long userId) {
        return Optional.ofNullable(findByUserIds(List.of(userId)).get(userId));
    }

    @Override
    public Map<Long, PersonalProfile> findByUserIds(List<Long> userIds) {
        return userIds.isEmpty() ? Map.of() : client.profiles(userIds);
    }
}

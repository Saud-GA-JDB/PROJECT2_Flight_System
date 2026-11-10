package com.ga.saudsFlightSystem.identity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PersonalProfileStore {
    Optional<PersonalProfile> findByUserId(Long userId);

    Map<Long, PersonalProfile> findByUserIds(List<Long> userIds);

    boolean existsByCpr(String cpr);

    void update(Long userId, PersonalProfileUpdate update);
}

package com.ga.saudsFlightSystem.identity;

import java.util.Optional;

public interface PersonalProfileStore {
    Optional<PersonalProfile> findByUserId(Long userId);

    boolean existsByCpr(String cpr);

    void update(Long userId, PersonalProfileUpdate update);
}

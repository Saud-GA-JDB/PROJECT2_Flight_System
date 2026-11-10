package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    boolean existsByCpr(String cpr);
}

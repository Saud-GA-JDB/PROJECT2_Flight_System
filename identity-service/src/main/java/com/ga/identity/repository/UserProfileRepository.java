package com.ga.identity.repository;

import com.ga.identity.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    boolean existsByCpr(String cpr);
}

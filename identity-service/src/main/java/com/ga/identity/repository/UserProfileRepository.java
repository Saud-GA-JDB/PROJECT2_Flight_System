package com.ga.identity.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import com.ga.identity.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    boolean existsByCpr(String cpr);

    @Query("SELECT p.imageUrl FROM UserProfile p WHERE p.userId = :userId")
    Optional<String> findImageUrlByUserId(@Param("userId") Long userId);
}

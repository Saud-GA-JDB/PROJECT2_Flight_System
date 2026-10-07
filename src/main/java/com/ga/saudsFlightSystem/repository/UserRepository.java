package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailAddress(String emailAddress);
    User findUserByEmailAddress(String emailAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.emailAddress = :email")
    User findUserForLogin(@Param("email") String email);

    @Transactional
    @Modifying
    @Query("update User u set u.failedLoginAttempts = 0, u.loginAttemptsDate = :today "
            + "where u.loginAttemptsDate is null or u.loginAttemptsDate < :today")
    int resetDailyLoginAttempts(@Param("today") LocalDate today);
}

package com.ga.identity.repository;

import com.ga.identity.model.User;
import com.ga.common.contract.identity.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query(value = """
        SELECT new com.ga.common.contract.identity.CustomerResponse(
            u.id,
            TRIM(CONCAT(COALESCE(p.fName, ''), ' ', COALESCE(p.lName, ''))),
            u.emailAddress)
        FROM User u
        LEFT JOIN u.personalProfile p
        WHERE u.role = :role AND u.status = :status AND u.isActive = true
          AND (CAST(u.id AS string) = :query
            OR LOCATE(:query, LOWER(u.emailAddress)) > 0
            OR LOCATE(:query, LOWER(TRIM(CONCAT(
                COALESCE(p.fName, ''), ' ', COALESCE(p.lName, ''))))) > 0)
        ORDER BY u.id
        """,
        countQuery = """
        SELECT COUNT(u.id)
        FROM User u
        LEFT JOIN u.personalProfile p
        WHERE u.role = :role AND u.status = :status AND u.isActive = true
          AND (CAST(u.id AS string) = :query
            OR LOCATE(:query, LOWER(u.emailAddress)) > 0
            OR LOCATE(:query, LOWER(TRIM(CONCAT(
                COALESCE(p.fName, ''), ' ', COALESCE(p.lName, ''))))) > 0)
        """)
    Page<CustomerResponse> findCustomers(@Param("query") String query,
            @Param("role") User.Role role, @Param("status") User.Status status, Pageable pageable);

    interface AccountState {
        Long getId();
        User.Role getRole();
        User.Status getStatus();
        boolean isActive();
        String getEmailAddress();
    }

    @Query("""
        SELECT u.id AS id, u.role AS role, u.status AS status,
               u.isActive AS active, u.emailAddress AS emailAddress
        FROM User u
        WHERE u.id = :userId
        """)
    Optional<AccountState> findAccountStateById(@Param("userId") Long userId);

    @Query("""
        SELECT u.id AS id, u.role AS role, u.status AS status,
               u.isActive AS active, u.emailAddress AS emailAddress
        FROM User u
        WHERE u.id IN :userIds
        """)
    List<AccountState> findAccountStatesByIds(@Param("userIds") List<Long> userIds);

    @Query("select u.emailAddress from User u where u.id = :userId")
    Optional<String> findEmailAddressById(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.provisioningKey = :key")
    Optional<User> findByProvisioningKeyForUpdate(@Param("key") String key);

    boolean existsByEmailAddress(String emailAddress);
    User findUserByEmailAddress(String emailAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.emailAddress = :email")
    User findUserForLogin(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.emailAddress = :email")
    User findByEmailAddressForUpdate(@Param("email") String email);

    @Transactional
    @Modifying
    @Query("update User u set u.failedLoginAttempts = 0, u.loginAttemptsDate = :today "
            + "where u.loginAttemptsDate is null or u.loginAttemptsDate < :today")
    int resetDailyLoginAttempts(@Param("today") LocalDate today);
}

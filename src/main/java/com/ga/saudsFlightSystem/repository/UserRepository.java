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
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailAddress(String emailAddress);
    User findUserByEmailAddress(String emailAddress);
    User findByAirlineEmployeeId(Long employeeId);
    User findByAtcEmployeeId(Long employeeId);

    @Query(value = """
        SELECT u.* FROM users u JOIN atc_employees e ON e.id = u.atc_employee_id
        WHERE u.role = 'ATC_EMPLOYEE' AND e.facility_id = :facilityId AND e.atc_role = 'CONTROLLER'
        ORDER BY e.id LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<User> findTowerControllers(@Param("facilityId") Long facilityId, @Param("size") int size, @Param("offset") long offset);

    @Query(value = """
        SELECT COUNT(*) FROM users u JOIN atc_employees e ON e.id = u.atc_employee_id
        WHERE u.role = 'ATC_EMPLOYEE' AND e.facility_id = :facilityId AND e.atc_role = 'CONTROLLER'
        """, nativeQuery = true)
    long countTowerControllers(@Param("facilityId") Long facilityId);

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

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
    @Query(value = """
        SELECT DISTINCT u.id FROM users u
        LEFT JOIN atc_employees t ON t.id = u.atc_employee_id
        LEFT JOIN control_facilities cf ON cf.id = t.facility_id
        LEFT JOIN airline_employees e ON e.id = u.airline_employee_id
        WHERE u.is_active = true AND u.status = :activeStatus AND (
          (u.role = 'ATC_EMPLOYEE' AND t.atc_role IN ('ADMIN', 'CONTROLLER') AND EXISTS (
            SELECT 1 FROM flights f WHERE f.id = :flightId
              AND (cf.airport_id = f.origin_airport_id OR cf.airport_id = f.destination_airport_id)))
          OR (u.role = 'AIRLINE_EMPLOYEE' AND e.airline_id = :airlineId AND e.airline_role = 'ADMIN')
          OR (u.role = 'AIRLINE_EMPLOYEE' AND e.airline_role = 'PILOT' AND EXISTS (
            SELECT 1 FROM crew_assignments c WHERE c.flight_id = :flightId
              AND c.employee_id = e.id AND c.duty IN ('PILOT', 'SECONDARY_PILOT'))))
        """, nativeQuery = true)
    List<Long> findFlightActionRecipients(@Param("flightId") Long flightId, @Param("airlineId") Long airlineId,
                                         @Param("activeStatus") int activeStatus);

    @Query(value = """
        SELECT DISTINCT u.id FROM users u
        LEFT JOIN atc_employees t ON t.id = u.atc_employee_id
        LEFT JOIN airline_employees e ON e.id = u.airline_employee_id
        WHERE u.is_active = true AND u.status = :activeStatus AND (
          (u.role = 'ATC_EMPLOYEE' AND t.facility_id = :facilityId AND t.atc_role IN ('ADMIN', 'CONTROLLER'))
          OR (u.role = 'AIRLINE_EMPLOYEE' AND e.airline_id = :airlineId AND e.airline_role = 'ADMIN')
          OR (u.role = 'AIRLINE_EMPLOYEE' AND e.airline_role = 'PILOT' AND EXISTS (
            SELECT 1 FROM crew_assignments c WHERE c.flight_id = :flightId
              AND c.employee_id = e.id AND c.duty IN ('PILOT', 'SECONDARY_PILOT'))))
        """, nativeQuery = true)
    List<Long> findClearanceRecipients(@Param("flightId") Long flightId, @Param("facilityId") Long facilityId,
                                       @Param("airlineId") Long airlineId, @Param("activeStatus") int activeStatus);

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

package com.ga.saudsFlightSystem.repository;

import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.ga.saudsFlightSystem.model.FAAAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FAAAdminRepository extends JpaRepository<FAAAdmin, Long> {

    @Query("""
        SELECT e FROM User u JOIN u.faaAdmin e
        WHERE u.id = :userId
        """)
    Optional<FAAAdmin> findByUserId(@Param("userId") Long userId);
}

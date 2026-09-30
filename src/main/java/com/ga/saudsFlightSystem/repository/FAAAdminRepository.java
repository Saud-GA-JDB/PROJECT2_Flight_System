package com.ga.saudsFlightSystem.repository;

import com.ga.saudsFlightSystem.model.FAAAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FAAAdminRepository extends JpaRepository<FAAAdmin, Long> {

}

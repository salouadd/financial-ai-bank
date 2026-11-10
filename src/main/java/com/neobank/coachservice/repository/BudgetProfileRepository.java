package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.BudgetProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetProfileRepository extends JpaRepository<BudgetProfile, UUID> {
    Optional<BudgetProfile> findByUserId(UUID userId);
}
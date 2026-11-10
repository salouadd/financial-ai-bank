package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.BudgetRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRecommendationRepository extends JpaRepository<BudgetRecommendation, UUID> {
    Page<BudgetRecommendation> findAllByUserId(UUID userId, Pageable pageable);
    List<BudgetRecommendation> findAllByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM BudgetRecommendation r WHERE r.expiresAt < :now")
    int purgeExpiredRecommendations(Instant now);

    void deleteAllByUserId(UUID userId);
}
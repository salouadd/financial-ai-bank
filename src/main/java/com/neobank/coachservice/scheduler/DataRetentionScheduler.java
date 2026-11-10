package com.neobank.coachservice.scheduler;

import com.neobank.coachservice.repository.BudgetRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);
    private final BudgetRecommendationRepository recommendationRepository;

    public DataRetentionScheduler(BudgetRecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    // Exécution quotidienne à 02:00 UTC
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredData() {
        Instant now = Instant.now();
        int deleted = recommendationRepository.purgeExpiredRecommendations(now);
        if (deleted > 0) {
            log.info("Purge RGPD/09-08 : {} recommandations expirées ont été définitivement supprimées.", deleted);
        }
    }
}
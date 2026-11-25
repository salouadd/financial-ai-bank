package com.neobank.coachservice.scheduler;

import com.neobank.coachservice.repository.BudgetRecommendationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataRetentionSchedulerTest {

    @Test
    void purgesRecommendationsUsingTheCurrentTime() {
        BudgetRecommendationRepository repository = mock(BudgetRecommendationRepository.class);
        DataRetentionScheduler scheduler = new DataRetentionScheduler(repository);

        scheduler.purgeExpiredData();

        verify(repository).purgeExpiredRecommendations(any(Instant.class));
    }
}

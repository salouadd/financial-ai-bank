package com.neobank.coachservice.service;

import com.neobank.coachservice.exception.RequestRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRateLimiterTest {

    @Test
    void limitsEachUserAndOperationIndependently() {
        UserRateLimiter limiter = new UserRateLimiter(1, 1);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        limiter.checkAiAnalysis(userA);
        assertThatThrownBy(() -> limiter.checkAiAnalysis(userA))
                .isInstanceOf(RequestRateLimitExceededException.class);
        assertThatCode(() -> limiter.checkAiAnalysis(userB)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkSalaryDetection(userA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkSalaryDetection(userA))
                .isInstanceOf(RequestRateLimitExceededException.class);
    }

    @Test
    void forgetRemovesOnlyTheSelectedUsersWindows() {
        UserRateLimiter limiter = new UserRateLimiter(1, 1);
        UUID erasedUser = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        limiter.checkAiAnalysis(erasedUser);
        limiter.checkSalaryDetection(otherUser);

        limiter.forget(erasedUser);

        assertThatCode(() -> limiter.checkAiAnalysis(erasedUser)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkSalaryDetection(otherUser))
                .isInstanceOf(RequestRateLimitExceededException.class);
    }
}

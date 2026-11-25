package com.neobank.coachservice.sca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ScaProvider {

    String issueChallenge(ChallengeRequest request);

    boolean verifyChallenge(VerificationRequest request);

    record ChallengeRequest(
            UUID userId,
            UUID intentId,
            UUID vaultId,
            BigDecimal amount,
            Instant expiresAt) {
    }

    record VerificationRequest(
            String challengeId,
            UUID userId,
            UUID intentId,
            UUID vaultId,
            BigDecimal amount,
            String code) {
    }
}

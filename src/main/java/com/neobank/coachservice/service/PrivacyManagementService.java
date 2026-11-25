package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.PrivacyDTOs.*;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.model.UserConsent;
import com.neobank.coachservice.exception.DataErasureBlockedException;
import com.neobank.coachservice.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PrivacyManagementService {

    private final UserConsentRepository consentRepository;
    private final BudgetProfileService profileService;
    private final SmartVaultService vaultService;
    private final BudgetRecommendationRepository recommendationRepository;
    private final BudgetProfileRepository profileRepository;
    private final SmartVaultRepository vaultRepository;
    private final AuditLogRepository auditLogRepository;
    private final VaultLockIntentRepository intentRepository;
    private final VaultLedgerEntryRepository ledgerRepository;
    private final UserRateLimiter userRateLimiter;

    public PrivacyManagementService(
            UserConsentRepository consentRepository,
            BudgetProfileService profileService,
            SmartVaultService vaultService,
            BudgetRecommendationRepository recommendationRepository,
            BudgetProfileRepository profileRepository,
            SmartVaultRepository vaultRepository,
            AuditLogRepository auditLogRepository,
            VaultLockIntentRepository intentRepository,
            VaultLedgerEntryRepository ledgerRepository,
            UserRateLimiter userRateLimiter) {
        this.consentRepository = consentRepository;
        this.profileService = profileService;
        this.vaultService = vaultService;
        this.recommendationRepository = recommendationRepository;
        this.profileRepository = profileRepository;
        this.vaultRepository = vaultRepository;
        this.auditLogRepository = auditLogRepository;
        this.intentRepository = intentRepository;
        this.ledgerRepository = ledgerRepository;
        this.userRateLimiter = userRateLimiter;
    }

    @Transactional(readOnly = true)
    public ConsentStatusResponse getConsentStatus(UUID userId) {
        return consentRepository.findByUserId(userId)
                .map(c -> new ConsentStatusResponse(c.isAiProcessingConsented(), c.getPolicyVersion(), c.getConsentedAt(), c.getRevokedAt()))
                .orElse(new ConsentStatusResponse(false, "NONE", null, null));
    }

    @Transactional
    public ConsentStatusResponse updateConsent(UUID userId, UpdateConsentRequest req, String clientIp) {
        UserConsent consent = consentRepository.findByUserId(userId)
                .orElseGet(() -> new UserConsent(userId, false, req.policyVersion(), clientIp));

        if (Boolean.TRUE.equals(req.consented())) {
            consent.grantConsent(req.policyVersion(), clientIp);
        } else {
            consent.revokeConsent(clientIp);
        }

        UserConsent saved = consentRepository.save(consent);

        auditLogRepository.save(new AuditLog(
                userId,
                saved.isAiProcessingConsented() ? "CONSENT_GRANTED" : "CONSENT_REVOKED",
                "USER_PRIVACY",
                saved.getId().toString(),
                String.format("{\"policyVersion\":\"%s\"}", req.policyVersion()),
                clientIp
        ));

        return new ConsentStatusResponse(saved.isAiProcessingConsented(), saved.getPolicyVersion(), saved.getConsentedAt(), saved.getRevokedAt());
    }

    /**
     * Droit à la portabilité (Exportation complète)
     */
    @Transactional(readOnly = true)
    public UserDataExportResponse exportUserData(UUID userId) {
        var profile = profileService.getProfile(userId);
        var vaults = vaultService.listUserVaults(userId);
        ConsentExportItem consent = consentRepository.findByUserId(userId)
                .map(c -> new ConsentExportItem(
                        c.isAiProcessingConsented(),
                        c.getPolicyVersion(),
                        c.getConsentedAt(),
                        c.getRevokedAt(),
                        c.getIpAddress(),
                        c.getUpdatedAt()))
                .orElse(null);
        var recos = recommendationRepository.findAllByUserId(userId).stream()
                .map(r -> new RecommendationHistoryItem(r.getId(), r.getSummary(), r.getPotentialMonthlySavings(), r.getRecommendationsPayload(), r.getCreatedAt()))
                .toList();
        List<LockIntentExportItem> intents = intentRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(intent -> new LockIntentExportItem(
                        intent.getId(),
                        intent.getVaultId(),
                        intent.getAmount(),
                        intent.getStatus().name(),
                        intent.getFailedAttempts(),
                        intent.getExpiresAt(),
                        intent.getCreatedAt()))
                .toList();
        List<LedgerEntryExportItem> ledgerEntries = ledgerRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(entry -> new LedgerEntryExportItem(
                        entry.getId(),
                        entry.getVaultId(),
                        entry.getIntentId(),
                        entry.getAmount(),
                        entry.getEntryType(),
                        entry.getCreatedAt()))
                .toList();
        List<AuditLogExportItem> auditLogs = auditLogRepository.findAllByUserIdOrderByTimestampAsc(userId).stream()
                .map(audit -> new AuditLogExportItem(
                        audit.getId(),
                        audit.getAction(),
                        audit.getResourceType(),
                        audit.getResourceId(),
                        audit.getDetails(),
                        audit.getIpAddress(),
                        audit.getTimestamp()))
                .toList();

        return new UserDataExportResponse(userId, Instant.now(), consent, profile, vaults, recos, intents, ledgerEntries, auditLogs);
    }

    /**
     * Droit à l'oubli / Effacement (Loi 09-08 / RGPD)
     */
    @Transactional
    public void deleteUserData(UUID userId) {
        List<SmartVault> activeVaults = vaultRepository.findAllByUserId(userId);
        boolean hasBalance = activeVaults.stream()
                .anyMatch(v -> v.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0);

        if (hasBalance) {
            throw new DataErasureBlockedException();
        }

        UUID anonymousUserId = UUID.randomUUID();

        recommendationRepository.deleteAllByUserId(userId);
        profileRepository.findByUserId(userId).ifPresent(profileRepository::delete);
        consentRepository.deleteByUserId(userId);

        ledgerRepository.anonymizeUserData(userId, anonymousUserId);
        intentRepository.anonymizeLedgerIntents(userId, anonymousUserId);
        intentRepository.deleteIntentsWithoutLedger(userId);
        vaultRepository.anonymizeVaultsWithLedger(userId, anonymousUserId);
        vaultRepository.deleteVaultsWithoutLedger(userId);

        auditLogRepository.anonymizeUserData(userId, anonymousUserId);
        auditLogRepository.save(new AuditLog(
                anonymousUserId, "DATA_ERASURE_COMPLETED", "USER_PRIVACY", null, "{}", null
        ));
        userRateLimiter.forget(userId);
    }
}
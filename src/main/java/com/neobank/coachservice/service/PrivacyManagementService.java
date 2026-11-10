package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.PrivacyDTOs.*;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.model.UserConsent;
import com.neobank.coachservice.repository.*;
import org.springframework.data.domain.Pageable;
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

    public PrivacyManagementService(
            UserConsentRepository consentRepository,
            BudgetProfileService profileService,
            SmartVaultService vaultService,
            BudgetRecommendationRepository recommendationRepository,
            BudgetProfileRepository profileRepository,
            SmartVaultRepository vaultRepository,
            AuditLogRepository auditLogRepository) {
        this.consentRepository = consentRepository;
        this.profileService = profileService;
        this.vaultService = vaultService;
        this.recommendationRepository = recommendationRepository;
        this.profileRepository = profileRepository;
        this.vaultRepository = vaultRepository;
        this.auditLogRepository = auditLogRepository;
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
        var consent = getConsentStatus(userId);
        var recos = recommendationRepository.findAllByUserId(userId).stream()
                .map(r -> new RecommendationHistoryItem(r.getId(), r.getSummary(), r.getPotentialMonthlySavings(), r.getRecommendationsPayload(), r.getCreatedAt()))
                .toList();

        return new UserDataExportResponse(userId, Instant.now(), consent, profile, vaults, recos);
    }

    /**
     * Droit à l'oubli / Effacement (Loi 09-08 / RGPD)
     */
    @Transactional
    public void deleteUserData(UUID userId, String clientIp) {
        // 1. Contrôle : refuser si des coffres contiennent encore des fonds
        List<SmartVault> activeVaults = vaultRepository.findAllByUserId(userId);
        boolean hasBalance = activeVaults.stream()
                .anyMatch(v -> v.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0);

        if (hasBalance) {
            throw new IllegalStateException("Effacement impossible : des coffres contiennent encore un solde actif. Videz-les préalablement.");
        }

        // 2. Suppression des recommandations et coffres
        recommendationRepository.deleteAllByUserId(userId);
        vaultRepository.deleteAll(activeVaults);

        // 3. Suppression du profil budgétaire
        profileRepository.findByUserId(userId).ifPresent(profileRepository::delete);

        // 4. Révocation définitive du consentement
        consentRepository.findByUserId(userId).ifPresent(c -> {
            c.revokeConsent(clientIp);
            consentRepository.save(c);
        });

        // 5. Audit de suppression sans conservation des PII
        auditLogRepository.save(new AuditLog(
                userId, "DATA_ERASURE_COMPLETED", "USER_PRIVACY", userId.toString(), "{}", clientIp
        ));
    }
}
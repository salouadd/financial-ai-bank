package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.BudgetProfileDTOs.BudgetProfileResponse;
import com.neobank.coachservice.dto.BudgetProfileDTOs.UpsertProfileRequest;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.BudgetProfile;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.BudgetProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class BudgetProfileService {

    private static final Logger log = LoggerFactory.getLogger(BudgetProfileService.class);

    private final BudgetProfileRepository profileRepository;
    private final AuditLogRepository auditLogRepository;

    public BudgetProfileService(BudgetProfileRepository profileRepository, AuditLogRepository auditLogRepository) {
        this.profileRepository = profileRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public BudgetProfileResponse getProfile(UUID userId) {
        BudgetProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> new BudgetProfile(userId, BigDecimal.ZERO, 50, 30, 20));
        return mapToResponse(profile);
    }

    @Transactional
    public BudgetProfileResponse upsertProfile(UUID userId, UpsertProfileRequest req, String clientIp) {
        boolean isCreation = false;

        BudgetProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            profile = new BudgetProfile(
                    userId,
                    req.netMonthlyIncome(),
                    req.needsRatio(),
                    req.wantsRatio(),
                    req.savingsRatio()
            );
            isCreation = true;
        } else {
            profile.update(
                    req.netMonthlyIncome(),
                    req.needsRatio(),
                    req.wantsRatio(),
                    req.savingsRatio()
            );
        }

        BudgetProfile saved = profileRepository.save(profile);

        // Enregistrement de l'audit trail
        String action = isCreation ? "CREATE_PROFILE" : "UPDATE_PROFILE";
        String detailsJson = String.format(
                "{\"needsRatio\":%d,\"wantsRatio\":%d,\"savingsRatio\":%d}",
                req.needsRatio(), req.wantsRatio(), req.savingsRatio()
        );

        AuditLog audit = new AuditLog(userId, action, "BUDGET_PROFILE", saved.getId().toString(), detailsJson, clientIp);
        auditLogRepository.save(audit);

        log.info("Action={} exécutée pour userId={} avec profileId={}", action, userId, saved.getId());

        return mapToResponse(saved);
    }

    private BudgetProfileResponse mapToResponse(BudgetProfile p) {
        return new BudgetProfileResponse(
                p.getId(),
                p.getUserId(),
                p.getNetMonthlyIncome(),
                p.getNeedsRatio(),
                p.getWantsRatio(),
                p.getSavingsRatio(),
                p.getVersion(),
                p.getUpdatedAt()
        );
    }
}
package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.VaultLockDTOs.LockConfirmRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockConfirmResponse;
import com.neobank.coachservice.dto.VaultLockDTOs.LockIntentRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockIntentResponse;
import com.neobank.coachservice.exception.ScaAttemptsExceededException;
import com.neobank.coachservice.exception.ScaChallengeExpiredException;
import com.neobank.coachservice.exception.ScaVerificationFailedException;
import com.neobank.coachservice.exception.IdempotencyKeyConflictException;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.model.VaultLedgerEntry;
import com.neobank.coachservice.model.VaultLockIntent;
import com.neobank.coachservice.repository.*;
import com.neobank.coachservice.sca.ScaProvider;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class VaultLockService {

    private static final int MAX_SCA_ATTEMPTS = 5;

    private final SmartVaultRepository vaultRepository;
    private final VaultLockIntentRepository intentRepository;
    private final VaultLedgerEntryRepository ledgerRepository;
    private final AuditLogRepository auditLogRepository;
    private final ScaProvider scaProvider;

    public VaultLockService(
            SmartVaultRepository vaultRepository,
            VaultLockIntentRepository intentRepository,
            VaultLedgerEntryRepository ledgerRepository,
            AuditLogRepository auditLogRepository,
            ScaProvider scaProvider) {
        this.vaultRepository = vaultRepository;
        this.intentRepository = intentRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditLogRepository = auditLogRepository;
        this.scaProvider = scaProvider;
    }

    /**
     * Étape 1 : Création de l'intention avec défi SCA
     */
    @Transactional
    public LockIntentResponse createLockIntent(UUID userId, UUID vaultId, LockIntentRequest req, String clientIp) {
        // Anti-IDOR : vérification d'appartenance
        SmartVault vault = vaultRepository.findByIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Coffre introuvable ou non autorisé."));

        // Règle de plafond unitaire (ex. max 50 000 MAD par opération)
        if (req.amount().compareTo(new BigDecimal("50000.00")) > 0) {
            throw new IllegalArgumentException("Le plafond par opération de verrouillage est de 50 000.00 MAD");
        }

        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        VaultLockIntent intent = new VaultLockIntent(userId, vaultId, req.amount(), "PENDING", expiresAt);
        VaultLockIntent saved = intentRepository.save(intent);
        String challengeId = scaProvider.issueChallenge(new ScaProvider.ChallengeRequest(
                userId, saved.getId(), vaultId, req.amount(), expiresAt));
        saved.setScaChallenge(challengeId);
        intentRepository.save(saved);

        AuditLog audit = new AuditLog(
                userId, "LOCK_INTENT_CREATED", "SMART_VAULT", vaultId.toString(),
                String.format("{\"intentId\":\"%s\",\"amount\":%s}", saved.getId(), req.amount()),
                clientIp
        );
        auditLogRepository.save(audit);

        return new LockIntentResponse(saved.getId(), vaultId, req.amount(), "PROVIDER_OTP", expiresAt);
    }

    /**
     * Étape 2 : Confirmation avec validation SCA et protection contre le rejeu
     */
    @Transactional(noRollbackFor = {
            ScaVerificationFailedException.class,
            ScaChallengeExpiredException.class,
            ScaAttemptsExceededException.class
    })
    public LockConfirmResponse confirmLock(
            UUID userId,
            UUID vaultId,
            String idempotencyKey,
            LockConfirmRequest req,
            String clientIp) {

        // Serialize requests using the same user-scoped key through transaction commit.
        ledgerRepository.lockIdempotencyKey(userId, idempotencyKey);
        Optional<VaultLedgerEntry> existing = ledgerRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), userId, vaultId, idempotencyKey, req.intentId());
        }

        // 2. Validation de l'intention
        VaultLockIntent intent = intentRepository.findByIdAndUserIdForUpdate(req.intentId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Intention de verrouillage expirée ou invalide."));

        existing = ledgerRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), userId, vaultId, idempotencyKey, req.intentId());
        }

        if (!intent.getVaultId().equals(vaultId)) {
            throw new IllegalArgumentException("L'intention ne correspond pas à ce coffre-fort.");
        }
        if (intent.getStatus() != VaultLockIntent.IntentStatus.PENDING) {
            throw new ScaChallengeExpiredException();
        }
        if (intent.isExpired()) {
            intent.markExpired();
            intentRepository.save(intent);
            throw new ScaChallengeExpiredException();
        }
        if (intent.getFailedAttempts() >= MAX_SCA_ATTEMPTS) {
            intent.markFailed();
            intentRepository.save(intent);
            throw new ScaAttemptsExceededException();
        }

        // The provider validates both possession and the exact transaction context.
        boolean verified = scaProvider.verifyChallenge(new ScaProvider.VerificationRequest(
                intent.getScaChallenge(),
                userId,
                intent.getId(),
                vaultId,
                intent.getAmount(),
                req.scaCode()));
        if (!verified) {
            intent.incrementFailedAttempts();
            if (intent.getFailedAttempts() >= MAX_SCA_ATTEMPTS) {
                intent.markFailed();
                intentRepository.save(intent);
                throw new ScaAttemptsExceededException();
            }
            intentRepository.save(intent);
            throw new ScaVerificationFailedException();
        }

        // 4. Crédit du coffre (protégé par @Version contre les accès concurrents)
        SmartVault vault = vaultRepository.findByIdAndUserIdForUpdate(vaultId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Coffre introuvable."));
        existing = ledgerRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), userId, vaultId, idempotencyKey, req.intentId());
        }
        vault.credit(intent.getAmount());
        vaultRepository.save(vault);

        // 5. Consignation dans le Ledger comptable en partie double
        VaultLedgerEntry ledgerEntry = new VaultLedgerEntry(
                userId,
                vaultId,
                intent.getId(),
                idempotencyKey,
                intent.getAmount(),
                "LOCK_INFLOW"
        );
        VaultLedgerEntry savedLedger = ledgerRepository.save(ledgerEntry);

        intent.markConfirmed();
        intentRepository.save(intent);

        // 6. Audit Trail
        AuditLog audit = new AuditLog(
                userId, "LOCK_CONFIRMED", "SMART_VAULT", vaultId.toString(),
                String.format("{\"ledgerId\":\"%s\",\"amount\":%s}", savedLedger.getId(), intent.getAmount()),
                clientIp
        );
        auditLogRepository.save(audit);

        return new LockConfirmResponse(
                savedLedger.getId(),
                vaultId,
                intent.getAmount(),
                vault.getCurrentBalance(),
                savedLedger.getCreatedAt(),
                "SUCCESS"
        );
    }

    private LockConfirmResponse replay(
            VaultLedgerEntry entry,
            UUID userId,
            UUID vaultId,
            String idempotencyKey,
            UUID intentId) {
        if (!entry.getUserId().equals(userId)
                || !entry.getVaultId().equals(vaultId)
                || !entry.getIntentId().equals(intentId)
                || !entry.getIdempotencyKey().equals(idempotencyKey)
                || !"LOCK_INFLOW".equals(entry.getEntryType())) {
            throw new IdempotencyKeyConflictException();
        }
        SmartVault vault = vaultRepository.findByIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Coffre introuvable."));
        return new LockConfirmResponse(
                entry.getId(),
                vaultId,
                entry.getAmount(),
                vault.getCurrentBalance(),
                entry.getCreatedAt(),
                "COMPLETED_IDEMPOTENT_REPLAY"
        );
    }
}
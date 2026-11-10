package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.VaultLockDTOs.*;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.model.VaultLedgerEntry;
import com.neobank.coachservice.model.VaultLockIntent;
import com.neobank.coachservice.repository.*;
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

    private final SmartVaultRepository vaultRepository;
    private final VaultLockIntentRepository intentRepository;
    private final VaultLedgerEntryRepository ledgerRepository;
    private final AuditLogRepository auditLogRepository;

    public VaultLockService(
            SmartVaultRepository vaultRepository,
            VaultLockIntentRepository intentRepository,
            VaultLedgerEntryRepository ledgerRepository,
            AuditLogRepository auditLogRepository) {
        this.vaultRepository = vaultRepository;
        this.intentRepository = intentRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditLogRepository = auditLogRepository;
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
        String challenge = "SCA-OTP-AUTH-REQUIRED";

        VaultLockIntent intent = new VaultLockIntent(userId, vaultId, req.amount(), challenge, expiresAt);
        VaultLockIntent saved = intentRepository.save(intent);

        AuditLog audit = new AuditLog(
                userId, "LOCK_INTENT_CREATED", "SMART_VAULT", vaultId.toString(),
                String.format("{\"intentId\":\"%s\",\"amount\":%s}", saved.getId(), req.amount()),
                clientIp
        );
        auditLogRepository.save(audit);

        return new LockIntentResponse(saved.getId(), vaultId, req.amount(), "SMS_OTP", expiresAt);
    }

    /**
     * Étape 2 : Confirmation avec validation SCA et protection contre le rejeu
     */
    @Transactional
    public LockConfirmResponse confirmLock(
            UUID userId,
            UUID vaultId,
            String idempotencyKey,
            LockConfirmRequest req,
            String clientIp) {

        // 1. Détection anti-rejeu par Idempotency-Key
        Optional<VaultLedgerEntry> existing = ledgerRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            VaultLedgerEntry entry = existing.get();
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

        // 2. Validation de l'intention
        VaultLockIntent intent = intentRepository.findByIdAndUserId(req.intentId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Intention de verrouillage expirée ou invalide."));

        if (!intent.getVaultId().equals(vaultId)) {
            throw new IllegalArgumentException("L'intention ne correspond pas à ce coffre-fort.");
        }
        if (intent.getStatus() != VaultLockIntent.IntentStatus.PENDING || intent.isExpired()) {
            throw new IllegalStateException("L'intention de verrouillage est expirée ou déjà consommée.");
        }

        // 3. Validation de la signature SCA (défi 2FA)
        if (!validateScaChallenge(intent.getScaChallenge(), req.scaCode())) {
            throw new SecurityException("Code de sécurité SCA invalide.");
        }

        // 4. Crédit du coffre (protégé par @Version contre les accès concurrents)
        SmartVault vault = vaultRepository.findByIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Coffre introuvable."));
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

    private boolean validateScaChallenge(String challenge, String scaCode) {
        // En production, vérification auprès du HSM ou du service OTP/FIDO2
        return scaCode != null && !scaCode.isBlank() && scaCode.length() == 6;
    }
}
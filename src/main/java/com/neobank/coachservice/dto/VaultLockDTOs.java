package com.neobank.coachservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class VaultLockDTOs {

    public record LockIntentRequest(
            @NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "1.00", message = "Le montant minimum de verrouillage est de 1.00 MAD")
            BigDecimal amount
    ) {}

    public record LockIntentResponse(
            UUID intentId,
            UUID vaultId,
            BigDecimal amount,
            String scaChallengeMethod,
            Instant expiresAt
    ) {}

    public record LockConfirmRequest(
            @NotNull(message = "L'identifiant d'intention est obligatoire")
            UUID intentId,

            @NotBlank(message = "Le code SCA (MFA) est obligatoire")
            String scaCode
    ) {}

    public record LockConfirmResponse(
            UUID transactionId,
            UUID vaultId,
            BigDecimal transferredAmount,
            BigDecimal newVaultBalance,
            Instant executedAt,
            String status
    ) {}
}
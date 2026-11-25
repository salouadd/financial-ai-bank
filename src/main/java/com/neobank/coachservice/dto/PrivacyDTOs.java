package com.neobank.coachservice.dto;

import com.neobank.coachservice.dto.BudgetProfileDTOs.BudgetProfileResponse;
import com.neobank.coachservice.dto.SmartVaultDTOs.VaultResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PrivacyDTOs {

    public record ConsentStatusResponse(
            boolean consented,
            String policyVersion,
            Instant consentedAt,
            Instant revokedAt
    ) {}

    public record UpdateConsentRequest(
            @NotNull(message = "Le statut d'accord est obligatoire")
            Boolean consented,

            @NotBlank(message = "La version de la politique est obligatoire")
            @Size(max = 32, message = "La version de la politique ne peut pas dépasser 32 caractères")
            String policyVersion
    ) {}

    public record RecommendationHistoryItem(
            UUID id,
            String summary,
            BigDecimal potentialMonthlySavings,
            String detailsPayload,
            Instant createdAt
    ) {}

    public record ConsentExportItem(
            boolean consented,
            String policyVersion,
            Instant consentedAt,
            Instant revokedAt,
            String clientIp,
            Instant updatedAt
    ) {}

    public record LockIntentExportItem(
            UUID id,
            UUID vaultId,
            BigDecimal amount,
            String status,
            int failedAttempts,
            Instant expiresAt,
            Instant createdAt
    ) {}

    public record LedgerEntryExportItem(
            UUID id,
            UUID vaultId,
            UUID intentId,
            BigDecimal amount,
            String entryType,
            Instant createdAt
    ) {}

    public record AuditLogExportItem(
            UUID id,
            String action,
            String resourceType,
            String resourceId,
            String details,
            String ipAddress,
            Instant timestamp
    ) {}

    public record UserDataExportResponse(
            UUID userId,
            Instant exportDate,
            ConsentExportItem consent,
            BudgetProfileResponse profile,
            List<VaultResponse> vaults,
            List<RecommendationHistoryItem> recommendationsHistory,
            List<LockIntentExportItem> lockIntents,
            List<LedgerEntryExportItem> ledgerEntries,
            List<AuditLogExportItem> auditLogs
    ) {}
}
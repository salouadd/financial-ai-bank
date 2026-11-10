package com.neobank.coachservice.dto;

import com.neobank.coachservice.dto.BudgetProfileDTOs.BudgetProfileResponse;
import com.neobank.coachservice.dto.SmartVaultDTOs.VaultResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
            String policyVersion
    ) {}

    public record RecommendationHistoryItem(
            UUID id,
            String summary,
            BigDecimal potentialMonthlySavings,
            String detailsPayload,
            Instant createdAt
    ) {}

    public record UserDataExportResponse(
            UUID userId,
            Instant exportDate,
            ConsentStatusResponse consent,
            BudgetProfileResponse profile,
            List<VaultResponse> vaults,
            List<RecommendationHistoryItem> recommendationsHistory
    ) {}
}
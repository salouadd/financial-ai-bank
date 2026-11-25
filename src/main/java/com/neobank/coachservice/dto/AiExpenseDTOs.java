package com.neobank.coachservice.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AiExpenseDTOs {

    // Entrée : Transactions agrégées transmises par le client ou un service interne
    public record WasteAnalysisRequest(
            @NotNull(message = "L'historique des dépenses catégorisées est obligatoire")
            @NotEmpty(message = "La liste des dépenses ne peut pas être vide")
            @Size(max = 9, message = "Au maximum 9 catégories de dépenses sont autorisées")
            Map<@NotBlank @Size(max = 40) String,
                    @NotNull @DecimalMin(value = "0.00") @DecimalMax(value = "1000000.00") @Digits(integer = 7, fraction = 2)
                            BigDecimal> categorizedExpenses,

            @Size(max = 160, message = "L'objectif financier ne peut pas dépasser 160 caractères")
            String userFinancialGoal
    ) {}

    // Sortie brute attendue du LLM (Structured Output)
    public record RawAiPlanResponse(
            String summary,
            List<RawAdviceItem> adviceList,
            List<String> detectedSubscriptions
    ) {
        public record RawAdviceItem(
                String category,
                String adviceType,
                BigDecimal citedAmount,
                BigDecimal suggestedSavings,
                String recommendation
        ) {}
    }

    // Sortie assainie et validée renvoyée au frontend après contrôle F7
    public record BudgetPlanResponse(
            String coachSummary,
            List<VerifiedAdvice> verifiedAdvices,
            List<String> activeSubscriptions,
            BigDecimal potentialMonthlySavings
    ) {
        public record VerifiedAdvice(
                String category,
                String adviceType,
                BigDecimal currentSpending,
                BigDecimal potentialSavings,
                String actionableTip
        ) {}
    }
}
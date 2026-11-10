package com.neobank.coachservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AiExpenseDTOs {

    // Entrée : Transactions agrégées transmises par le client ou un service interne
    public record WasteAnalysisRequest(
            @NotNull(message = "L'historique des dépenses catégorisées est obligatoire")
            @NotEmpty(message = "La liste des dépenses ne peut pas être vide")
            Map<String, BigDecimal> categorizedExpenses,

            String userFinancialGoal // Optionnel, ex: "Constitution de mon apport immobilier"
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
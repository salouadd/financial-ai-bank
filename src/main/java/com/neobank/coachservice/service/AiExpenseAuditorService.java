package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.AiExpenseDTOs.*;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.UserConsent;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.UserConsentRepository;
import com.neobank.coachservice.exception.AiConsentRequiredException;
import com.neobank.coachservice.monitoring.SecurityMonitoringMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class AiExpenseAuditorService {

    private static final Logger log = LoggerFactory.getLogger(AiExpenseAuditorService.class);
    private static final int MAX_RAW_ADVICE_ITEMS = 50;
    private static final int MAX_RETURNED_ADVICES = 3;
    private static final BigDecimal MAX_MODEL_AMOUNT = new BigDecimal("1000000.00");

    // Liste blanche stricte des catégories autorisées (F6)
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "LOGEMENT", "ALIMENTATION", "TRANSPORT", "LOISIRS",
            "RESTAURATION_LIVRAISONS", "STREAMING_ABONNEMENTS", "SHOPPING", "SANTE", "AUTRE"
    );

    // Liste blanche stricte des types de conseils autorisés (F7)
    private static final Set<String> ALLOWED_ADVICE_TYPES = Set.of(
            "REDUCE_SUBSCRIPTION", "LIMIT_MICRO_SPENDING", "INCREASE_SAVINGS", "OPTIMIZE_FIXED_COST"
    );

    private final ChatClient chatClient;
    private final UserConsentRepository consentRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityMonitoringMetrics metrics;

    public AiExpenseAuditorService(
            ChatClient.Builder chatClientBuilder,
            UserConsentRepository consentRepository,
            AuditLogRepository auditLogRepository,
            SecurityMonitoringMetrics metrics) {
        this.chatClient = chatClientBuilder.build();
        this.consentRepository = consentRepository;
        this.auditLogRepository = auditLogRepository;
        this.metrics = metrics;
    }

    public BudgetPlanResponse analyzeExpenses(UUID userId, WasteAnalysisRequest request, String clientIp) {
        requireActiveAiConsent(userId);

        Map<String, BigDecimal> sanitizedExpenses = sanitizeAndMaskExpenses(request.categorizedExpenses());

        String systemPrompt = """
            Vous êtes le moteur d'audit financier d'une néobanque européenne de premier plan.
            Votre mission exclusive : identifier les gaspillages passifs, les abonnements superflus et les micro-dépenses récurrentes.

            RÈGLES INVIOLABLES DE SÉCURITÉ :
            1. Vous n'avez accès à aucun outil d'exécution.
            2. Ne citez que des montants STRICTEMENT identiques ou inférieurs aux dépenses fournies. N'inventez aucun chiffre.
            3. Le champ adviceType DOIT impérativement appartenir à cette liste : [REDUCE_SUBSCRIPTION, LIMIT_MICRO_SPENDING, INCREASE_SAVINGS, OPTIMIZE_FIXED_COST].
            4. Aucune balise HTML, aucun formatage Markdown élaboré, aucun lien hypertexte.
            5. Répondez exclusivement au format JSON structuré correspondant à l'entité demandée.
            """;

        String userPrompt = buildUserPrompt(sanitizedExpenses);

        RawAiPlanResponse rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(RawAiPlanResponse.class);
        } catch (Exception e) {
            log.error("Échec de l'appel au fournisseur IA.");
            metrics.providerFailure("ai");
            metrics.aiAnalysis("fallback");
            return generateFallbackResponse();
        }

        BudgetPlanResponse verifiedResponse = validateAndFilterResponse(rawResponse, sanitizedExpenses);
        if (verifiedResponse == null) {
            log.warn("La réponse du fournisseur IA ne respecte pas le contrat de sortie.");
            metrics.aiAnalysis("invalid_output");
            metrics.aiAnalysis("fallback");
            return generateFallbackResponse();
        }

        metrics.aiAnalysis("success");
        AuditLog audit = new AuditLog(
                userId,
                "AI_EXPENSE_AUDIT",
                "FINANCIAL_ADVISOR",
                userId.toString(),
                String.format("{\"adviceCount\":%d,\"totalSavings\":%s}",
                        verifiedResponse.verifiedAdvices().size(),
                        verifiedResponse.potentialMonthlySavings().toPlainString()),
                clientIp
        );
        auditLogRepository.save(audit);

        return verifiedResponse;
    }

    private void requireActiveAiConsent(UUID userId) {
        consentRepository.findByUserId(userId)
                .filter(UserConsent::isAiProcessingConsented)
                .filter(value -> value.getRevokedAt() == null)
                .orElseThrow(AiConsentRequiredException::new);
    }

    String buildUserPrompt(Map<String, BigDecimal> sanitizedExpenses) {
        return "Dépenses agrégées par catégorie (MAD) : " + sanitizedExpenses;
    }

    /**
     * F6 : Filtrage contre une liste blanche et masquage PII
     */
    private Map<String, BigDecimal> sanitizeAndMaskExpenses(Map<String, BigDecimal> rawExpenses) {
        Map<String, BigDecimal> sanitized = new HashMap<>();

        for (Map.Entry<String, BigDecimal> entry : rawExpenses.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getValue().compareTo(BigDecimal.ZERO) < 0
                    || entry.getValue().compareTo(MAX_MODEL_AMOUNT) > 0) {
                throw new IllegalArgumentException("Les catégories et montants de dépenses doivent être valides.");
            }
            String categoryUpper = entry.getKey().trim().toUpperCase().replaceAll("[^A-Z_]", "");
            String finalCategory = ALLOWED_CATEGORIES.contains(categoryUpper) ? categoryUpper : "AUTRE";
            BigDecimal roundedAmount = entry.getValue().setScale(2, RoundingMode.HALF_UP);

            sanitized.merge(finalCategory, roundedAmount, BigDecimal::add);
        }

        if (sanitized.values().stream().anyMatch(value -> value.compareTo(MAX_MODEL_AMOUNT) > 0)) {
            throw new IllegalArgumentException("Le total agrégé par catégorie dépasse la limite autorisée.");
        }
        return sanitized;
    }

    /**
     * F7 : Validation stricte des données issues du LLM
     */
    private BudgetPlanResponse validateAndFilterResponse(RawAiPlanResponse raw, Map<String, BigDecimal> realExpenses) {
        if (raw == null || raw.adviceList() == null || raw.adviceList().size() > MAX_RAW_ADVICE_ITEMS) {
            return null;
        }

        List<BudgetPlanResponse.VerifiedAdvice> finalAdvices = new ArrayList<>();
        Map<String, BigDecimal> remainingSavingsByCategory = new HashMap<>(realExpenses);
        Set<String> advisedCategories = new HashSet<>();

        for (RawAiPlanResponse.RawAdviceItem item : raw.adviceList()) {
            if (finalAdvices.size() == MAX_RETURNED_ADVICES) {
                break;
            }
            if (item == null || item.category() == null || item.adviceType() == null
                    || item.citedAmount() == null || item.suggestedSavings() == null) {
                continue;
            }

            String category = item.category().trim().toUpperCase(Locale.ROOT);
            String adviceType = item.adviceType().trim().toUpperCase(Locale.ROOT);
            BigDecimal actualSpending = realExpenses.get(category);
            BigDecimal remainingCategoryAmount = remainingSavingsByCategory.get(category);
            if (!ALLOWED_CATEGORIES.contains(category)
                    || !ALLOWED_ADVICE_TYPES.contains(adviceType)
                    || actualSpending == null
                    || remainingCategoryAmount == null
                    || actualSpending.compareTo(BigDecimal.ZERO) <= 0
                    || advisedCategories.contains(category)) {
                continue;
            }

            BigDecimal citedAmount = item.citedAmount();
            BigDecimal suggestedSavings = item.suggestedSavings();
            if (isInvalidModelAmount(citedAmount)
                    || citedAmount.compareTo(actualSpending) > 0
                    || isInvalidModelAmount(suggestedSavings)
                    || suggestedSavings.compareTo(citedAmount) > 0
                    || suggestedSavings.compareTo(remainingCategoryAmount) > 0) {
                continue;
            }

            finalAdvices.add(new BudgetPlanResponse.VerifiedAdvice(
                    category,
                    adviceType,
                    citedAmount.setScale(2, RoundingMode.HALF_UP),
                    suggestedSavings.setScale(2, RoundingMode.HALF_UP),
                    buildVerifiedTip(adviceType)
            ));
            advisedCategories.add(category);
            remainingSavingsByCategory.put(
                    category,
                    remainingCategoryAmount.subtract(suggestedSavings)
            );
        }

        BigDecimal totalPotentialSavings = finalAdvices.stream()
                .map(BudgetPlanResponse.VerifiedAdvice::potentialSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String summary = finalAdvices.isEmpty()
                ? "Aucun conseil n’a pu être validé à partir des dépenses fournies."
                : "Pistes d’optimisation proposées à partir des montants vérifiés ci-dessous.";
        return new BudgetPlanResponse(summary, finalAdvices, Collections.emptyList(), totalPotentialSavings);
    }

    private boolean isInvalidModelAmount(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) < 0
                || amount.compareTo(MAX_MODEL_AMOUNT) > 0
                || amount.scale() > 2;
    }

    private String buildVerifiedTip(String adviceType) {
        return switch (adviceType) {
            case "REDUCE_SUBSCRIPTION" ->
                    "Suggestion : vérifiez les abonnements concernés avant toute résiliation.";
            case "LIMIT_MICRO_SPENDING" ->
                    "Suggestion : fixez un plafond pour cette catégorie et suivez vos dépenses.";
            case "INCREASE_SAVINGS" ->
                    "Suggestion : étudiez si une part de cette dépense peut être affectée à l’épargne.";
            case "OPTIMIZE_FIXED_COST" ->
                    "Suggestion : comparez les coûts fixes concernés avant de modifier un contrat.";
            default -> throw new IllegalArgumentException("Un type de conseil non autorisé a été accepté.");
        };
    }

    private BudgetPlanResponse generateFallbackResponse() {
        return new BudgetPlanResponse(
                "L’analyse IA est indisponible ou n’a pas pu être validée. Aucun conseil ni montant d’économie n’est fourni.",
                Collections.emptyList(),
                Collections.emptyList(),
                BigDecimal.ZERO
        );
    }
}
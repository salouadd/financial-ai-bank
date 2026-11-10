package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.AiExpenseDTOs.*;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.BudgetProfile;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.BudgetProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiExpenseAuditorService {

    private static final Logger log = LoggerFactory.getLogger(AiExpenseAuditorService.class);

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
    private final BudgetProfileRepository profileRepository;
    private final AuditLogRepository auditLogRepository;

    public AiExpenseAuditorService(
            ChatClient.Builder chatClientBuilder,
            BudgetProfileRepository profileRepository,
            AuditLogRepository auditLogRepository) {
        this.chatClient = chatClientBuilder.build();
        this.profileRepository = profileRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public BudgetPlanResponse analyzeExpenses(UUID userId, WasteAnalysisRequest request, String clientIp) {
        // 1. Récupération du profil budgétaire pour contexte
        BudgetProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> new BudgetProfile(userId, new BigDecimal("10000.00"), 50, 30, 20));

        // 2. Étape F6.2 & F6.3 : Normalisation, filtrage sur liste blanche et arrondi
        Map<String, BigDecimal> sanitizedExpenses = sanitizeAndMaskExpenses(request.categorizedExpenses());

        // 3. Prompt Système scellé : instruction fixe, aucun outil externe, interdiction stricte de markdown libre
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

        String sanitizedGoal = sanitizeUserInput(request.userFinancialGoal());
        String userPrompt = String.format(
                "Revenu net : %s MAD. Objectif client : %s. Dépenses réelles agrégées par catégorie : %s",
                profile.getNetMonthlyIncome().toPlainString(),
                sanitizedGoal != null ? sanitizedGoal : "Optimisation générale de l'épargne",
                sanitizedExpenses.toString()
        );

        // 4. Étape F6.4 & F6.5 : Appel Spring AI avec Structured Output
        RawAiPlanResponse rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(RawAiPlanResponse.class);
        } catch (Exception e) {
            log.error("Échec de l'appel Spring AI pour userId={} : {}", userId, e.getMessage());
            return generateFallbackResponse(sanitizedExpenses);
        }

        // 5. Étape F7 : Validation déterministe en Java (Garde-fous post-LLM)
        BudgetPlanResponse verifiedResponse = validateAndFilterResponse(rawResponse, sanitizedExpenses);

        // 6. Audit Trail
        AuditLog audit = new AuditLog(
                userId,
                "AI_EXPENSE_AUDIT",
                "FINANCIAL_ADVISOR",
                profile.getId().toString(),
                String.format("{\"adviceCount\":%d,\"totalSavings\":%s}",
                        verifiedResponse.verifiedAdvices().size(),
                        verifiedResponse.potentialMonthlySavings().toPlainString()),
                clientIp
        );
        auditLogRepository.save(audit);

        return verifiedResponse;
    }

    /**
     * F6 : Filtrage contre une liste blanche et masquage PII
     */
    private Map<String, BigDecimal> sanitizeAndMaskExpenses(Map<String, BigDecimal> rawExpenses) {
        Map<String, BigDecimal> sanitized = new HashMap<>();

        for (Map.Entry<String, BigDecimal> entry : rawExpenses.entrySet()) {
            String categoryUpper = entry.getKey().trim().toUpperCase().replaceAll("[^A-Z_]", "");

            // Rejet ou classification en AUTRE si catégorie non conforme
            String finalCategory = ALLOWED_CATEGORIES.contains(categoryUpper) ? categoryUpper : "AUTRE";
            BigDecimal roundedAmount = entry.getValue().setScale(2, RoundingMode.HALF_UP);

            sanitized.merge(finalCategory, roundedAmount, BigDecimal::add);
        }

        return sanitized;
    }

    /**
     * F7 : Validation stricte des données issues du LLM
     */
    private BudgetPlanResponse validateAndFilterResponse(RawAiPlanResponse raw, Map<String, BigDecimal> realExpenses) {
        if (raw == null || raw.adviceList() == null) {
            return generateFallbackResponse(realExpenses);
        }

        List<BudgetPlanResponse.VerifiedAdvice> verifiedList = new ArrayList<>();
        BigDecimal totalPotentialSavings = BigDecimal.ZERO;

        for (RawAiPlanResponse.RawAdviceItem item : raw.adviceList()) {
            if (item == null) continue;

            // 1. Vérification du type de conseil
            if (!ALLOWED_ADVICE_TYPES.contains(item.adviceType())) {
                continue; // Rejet des types non certifiés
            }

            // 2. Vérification de la catégorie
            String cat = item.category() != null ? item.category().toUpperCase() : "AUTRE";
            BigDecimal actualSpending = realExpenses.get(cat);
            if (actualSpending == null || actualSpending.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // L'IA a inventé une dépense sur une catégorie inexistante -> rejet
            }

            // 3. Comparaison mathématique stricte (anti-hallucination)
            BigDecimal cited = item.citedAmount() != null ? item.citedAmount() : actualSpending;
            if (cited.compareTo(actualSpending) > 0) {
                cited = actualSpending; // Tronquage au montant réel si l'IA gonfle le chiffre
            }

            BigDecimal savings = item.suggestedSavings() != null ? item.suggestedSavings() : BigDecimal.ZERO;
            if (savings.compareTo(actualSpending) > 0) {
                savings = actualSpending.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
            }

            // 4. Nettoyage HTML et URLs
            String sanitizedTip = sanitizeText(item.recommendation());

            verifiedList.add(new BudgetPlanResponse.VerifiedAdvice(
                    cat,
                    item.adviceType(),
                    cited,
                    savings,
                    sanitizedTip
            ));

            totalPotentialSavings = totalPotentialSavings.add(savings);
        }

        // Limite stricte : maximum 3 conseils pertinents présentés au client
        List<BudgetPlanResponse.VerifiedAdvice> finalAdvices = verifiedList.stream()
                .limit(3)
                .toList();

        // Nettoyage de la synthèse
        String cleanSummary = sanitizeText(raw.summary());
        if (cleanSummary.isBlank()) {
            cleanSummary = "Analyse complétée. Des pistes d'optimisation ont été détectées sur vos postes de dépenses.";
        }

        List<String> cleanSubs = raw.detectedSubscriptions() != null
                ? raw.detectedSubscriptions().stream().map(this::sanitizeText).filter(s -> !s.isBlank()).toList()
                : Collections.emptyList();

        return new BudgetPlanResponse(cleanSummary, finalAdvices, cleanSubs, totalPotentialSavings);
    }

    private String sanitizeUserInput(String input) {
        if (input == null) return null;
        return input.replaceAll("[\\r\\n]", " ")
                .replaceAll("[<>{}\"\'`\\[\\]]", "")
                .trim();
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]*>", "") // Supprime toute balise HTML
                .replaceAll("https?://\\S+", "") // Supprime tout lien URL
                .replaceAll("[*#_`~]", "") // Supprime le markdown gras/italique/titres
                .trim();
    }

    private BudgetPlanResponse generateFallbackResponse(Map<String, BigDecimal> realExpenses) {
        return new BudgetPlanResponse(
                "Analyse basée sur les règles financières standard : privilégiez le plafonnement de vos charges discrétionnaires.",
                List.of(new BudgetPlanResponse.VerifiedAdvice(
                        "LOISIRS",
                        "LIMIT_MICRO_SPENDING",
                        realExpenses.getOrDefault("LOISIRS", BigDecimal.ZERO),
                        realExpenses.getOrDefault("LOISIRS", BigDecimal.ZERO).multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP),
                        "Plafonnez vos dépenses de loisirs hebdomadaires pour dégager une marge de sécurité financière."
                )),
                Collections.emptyList(),
                BigDecimal.ZERO
        );
    }
}
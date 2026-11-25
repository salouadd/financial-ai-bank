package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.AiExpenseDTOs.*;
import com.neobank.coachservice.exception.AiConsentRequiredException;
import com.neobank.coachservice.exception.GlobalExceptionHandler;
import com.neobank.coachservice.model.UserConsent;
import com.neobank.coachservice.monitoring.SecurityMonitoringMetrics;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.UserConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiExpenseAuditorServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserConsentRepository consentRepository;

    private AiExpenseAuditorService auditorService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        meterRegistry = new SimpleMeterRegistry();
        auditorService = new AiExpenseAuditorService(
                chatClientBuilder, consentRepository, auditLogRepository, new SecurityMonitoringMetrics(meterRegistry));
    }

    @Test
    @DisplayName("F7 : Doit rejeter les conseils de l'IA sur des montants ou catégories inexistants")
    void shouldFilterOutHallucinatedAdvice() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(RawAiPlanResponse.class))
                .thenReturn(null);

        // L'utilisateur ne dépense que 200 MAD en streaming
        WasteAnalysisRequest req = new WasteAnalysisRequest(
                Map.of("STREAMING_ABONNEMENTS", new BigDecimal("200.00")),
                "Épargner"
        );

        // Simulation d'une défaillance du LLM qui est gérée par le fallback sécurisé
        BudgetPlanResponse response = auditorService.analyzeExpenses(userId, req, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.coachSummary()).contains("indisponible");
        assertThat(response.verifiedAdvices()).isEmpty();
        assertThat(response.potentialMonthlySavings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(meterRegistry.get("coach.ai.analysis.events")
                .tag("outcome", "invalid_output")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void refusesAnalysisWhenConsentIsMissingBeforeCallingTheModel() {
        UUID userId = UUID.randomUUID();
        when(consentRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", BigDecimal.TEN), null),
                "127.0.0.1"
        )).isInstanceOf(AiConsentRequiredException.class);

        verifyNoInteractions(chatClient, auditLogRepository);
    }

    @Test
    void mapsMissingAiConsentToForbiddenResponse() {
        var problem = new GlobalExceptionHandler().handleAiConsentRequired(new AiConsentRequiredException());

        assertThat(problem.getStatus()).isEqualTo(403);
    }

    @Test
    void refusesAnalysisWhenConsentHasBeenRevokedBeforeCallingTheModel() {
        UUID userId = UUID.randomUUID();
        UserConsent consent = new UserConsent(userId, true, "v1", "127.0.0.1");
        consent.revokeConsent("127.0.0.1");
        when(consentRepository.findByUserId(userId)).thenReturn(Optional.of(consent));

        assertThatThrownBy(() -> auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", BigDecimal.TEN), "objectif privé"),
                "127.0.0.1"
        )).isInstanceOf(AiConsentRequiredException.class);

        verifyNoInteractions(chatClient, auditLogRepository);
    }

    @Test
    void sendsOnlySanitizedExpenseAggregatesToTheModel() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(RawAiPlanResponse.class))
                .thenReturn(new RawAiPlanResponse("Analyse", List.of(), List.of()));
        clearInvocations(chatClient);
        WasteAnalysisRequest request = new WasteAnalysisRequest(
                Map.of("LOISIRS", new BigDecimal("25.50")),
                "objectif privé"
        );

        auditorService.analyzeExpenses(userId, request, "127.0.0.1");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt().system(anyString()), times(2)).user(prompt.capture());
        assertThat(prompt.getAllValues()).anySatisfy(value -> {
            assertThat(value).contains("LOISIRS", "25.50");
            assertThat(value).doesNotContain("objectif privé", userId.toString(), "Revenu net");
        });
        verify(consentRepository).findByUserId(userId);
    }

    @Test
    void capsReturnedAdviceAndRecalculatesSavingsFromVisibleAdviceOnly() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        stubModelResponse(new RawAiPlanResponse(
                "Résumé",
                List.of(
                        advice("LOISIRS", "LIMIT_MICRO_SPENDING", "100", "40", "Conseil 1"),
                        advice("ALIMENTATION", "OPTIMIZE_FIXED_COST", "80", "30", "Conseil 2"),
                        advice("TRANSPORT", "INCREASE_SAVINGS", "60", "20", "Conseil 3"),
                        advice("SANTE", "REDUCE_SUBSCRIPTION", "50", "10", "Conseil 4 non affiché")
                ),
                List.of("Sub 1", "Sub 2", "Sub 3", "Sub 4", "Sub 5", "Sub 6")
        ));

        BudgetPlanResponse response = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of(
                        "LOISIRS", new BigDecimal("100"),
                        "ALIMENTATION", new BigDecimal("80"),
                        "TRANSPORT", new BigDecimal("60"),
                        "SANTE", new BigDecimal("50")
                ), null),
                "127.0.0.1"
        );

        assertThat(response.verifiedAdvices()).hasSize(3);
        assertThat(response.verifiedAdvices()).extracting(
                BudgetPlanResponse.VerifiedAdvice::potentialSavings
        ).containsExactly(new BigDecimal("40.00"), new BigDecimal("30.00"), new BigDecimal("20.00"));
        assertThat(response.potentialMonthlySavings()).isEqualByComparingTo("90.00");
        assertThat(response.activeSubscriptions()).isEmpty();
    }

    @Test
    void rejectsNegativeAmountsUnknownCategoriesAndPerCategorySavingsAboveSpending() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        stubModelResponse(new RawAiPlanResponse(
                "Résumé valide",
                List.of(
                        advice("LOISIRS", "LIMIT_MICRO_SPENDING", "20", "-1", "Montant négatif"),
                        advice("LOISIRS", "LIMIT_MICRO_SPENDING", "-1", "10", "Dépense négative"),
                        advice("INCONNUE", "LIMIT_MICRO_SPENDING", "10", "5", "Catégorie inventée"),
                        advice("LOISIRS", "LIMIT_MICRO_SPENDING", "80", "50", "Épargne exagérée"),
                        advice("LOISIRS", "LIMIT_MICRO_SPENDING", "20", "30", "Épargne supérieure au montant cité"),
                        advice("LOISIRS", "LIMIT_MICRO_SPENDING", "20", "20", "Conseil cohérent"),
                        advice("LOISIRS", "OPTIMIZE_FIXED_COST", "20", "5", "Conseil en doublon")
                ),
                null
        ));

        BudgetPlanResponse response = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", new BigDecimal("20")), null),
                "127.0.0.1"
        );

        assertThat(response.verifiedAdvices()).hasSize(1);
        assertThat(response.verifiedAdvices().getFirst().actionableTip()).startsWith("Suggestion :");
        assertThat(response.potentialMonthlySavings()).isEqualByComparingTo("20.00");
    }

    @Test
    void usesExplicitEmptyFallbackForMalformedModelResponsesAndBoundsText() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        stubModelResponse(new RawAiPlanResponse("Résumé", null, null));

        BudgetPlanResponse malformed = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", new BigDecimal("20")), null),
                "127.0.0.1"
        );
        assertThat(malformed.coachSummary()).contains("Aucun conseil");
        assertThat(malformed.verifiedAdvices()).isEmpty();
        assertThat(malformed.potentialMonthlySavings()).isEqualByComparingTo(BigDecimal.ZERO);

        stubModelResponse(new RawAiPlanResponse(
                "x".repeat(400),
                List.of(advice("LOISIRS", "LIMIT_MICRO_SPENDING", "20", "5", "y".repeat(500))),
                List.of("Abonnement inventé")
        ));
        BudgetPlanResponse bounded = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", new BigDecimal("20")), null),
                "127.0.0.1"
        );
        assertThat(bounded.coachSummary()).isEqualTo(
                "Pistes d’optimisation proposées à partir des montants vérifiés ci-dessous."
        );
        assertThat(bounded.verifiedAdvices().getFirst().actionableTip()).doesNotContain("y".repeat(100));
        assertThat(bounded.activeSubscriptions()).isEmpty();
    }

    @Test
    void sanitizesHtmlAndLinksFromModelText() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        stubModelResponse(new RawAiPlanResponse(
                "<b>Résumé</b> https://example.com *sûr*",
                List.of(advice("LOISIRS", "LIMIT_MICRO_SPENDING", "20", "5",
                        "<script>alert(1)</script> Voir https://example.com")),
                java.util.Arrays.asList("<img src=x>Streaming", null)
        ));

        BudgetPlanResponse response = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", new BigDecimal("20")), null),
                "127.0.0.1"
        );

        assertThat(response.coachSummary()).doesNotContain("Résumé", "https://", "sûr");
        assertThat(response.verifiedAdvices().getFirst().actionableTip())
                .startsWith("Suggestion :")
                .doesNotContain("<", ">", "https://", "alert(1)", "Voir");
        assertThat(response.activeSubscriptions()).isEmpty();
    }

    @Test
    void fallsBackWhenModelReturnsTooManyOrIncompleteAdviceItems() {
        UUID userId = UUID.randomUUID();
        grantConsent(userId);
        RawAiPlanResponse.RawAdviceItem validAdvice =
                advice("LOISIRS", "LIMIT_MICRO_SPENDING", "20", "5", "Conseil");
        List<RawAiPlanResponse.RawAdviceItem> tooMany = java.util.Collections.nCopies(51, validAdvice);
        stubModelResponse(new RawAiPlanResponse("Résumé", tooMany, null));

        BudgetPlanResponse oversized = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", new BigDecimal("20")), null),
                "127.0.0.1"
        );
        assertThat(oversized.coachSummary()).contains("indisponible");
        assertThat(oversized.verifiedAdvices()).isEmpty();
        assertThat(oversized.potentialMonthlySavings()).isEqualByComparingTo(BigDecimal.ZERO);

        stubModelResponse(new RawAiPlanResponse(
                "Résumé",
                java.util.Collections.singletonList(
                        new RawAiPlanResponse.RawAdviceItem("LOISIRS", "LIMIT_MICRO_SPENDING", null, BigDecimal.ONE, "Conseil")
                ),
                null
        ));
        BudgetPlanResponse incomplete = auditorService.analyzeExpenses(
                userId,
                new WasteAnalysisRequest(Map.of("LOISIRS", new BigDecimal("20")), null),
                "127.0.0.1"
        );
        assertThat(incomplete.coachSummary()).contains("Aucun conseil");
        assertThat(incomplete.verifiedAdvices()).isEmpty();
    }

    private void stubModelResponse(RawAiPlanResponse response) {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(RawAiPlanResponse.class))
                .thenReturn(response);
    }

    private static RawAiPlanResponse.RawAdviceItem advice(
            String category,
            String adviceType,
            String cited,
            String savings,
            String recommendation) {
        return new RawAiPlanResponse.RawAdviceItem(
                category,
                adviceType,
                new BigDecimal(cited),
                new BigDecimal(savings),
                recommendation
        );
    }

    private void grantConsent(UUID userId) {
        when(consentRepository.findByUserId(userId))
                .thenReturn(Optional.of(new UserConsent(userId, true, "v1", "127.0.0.1")));
    }
}

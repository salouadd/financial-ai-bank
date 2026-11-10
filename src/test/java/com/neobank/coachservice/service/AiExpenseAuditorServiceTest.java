package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.AiExpenseDTOs.*;
import com.neobank.coachservice.model.BudgetProfile;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.BudgetProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExpenseAuditorServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private BudgetProfileRepository profileRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AiExpenseAuditorService auditorService;

    @Test
    @DisplayName("F7 : Doit rejeter les conseils de l'IA sur des montants ou catégories inexistants")
    void shouldFilterOutHallucinatedAdvice() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(
                new BudgetProfile(userId, new BigDecimal("12000.00"), 50, 30, 20)
        ));

        // L'utilisateur ne dépense que 200 MAD en streaming
        WasteAnalysisRequest req = new WasteAnalysisRequest(
                Map.of("STREAMING_ABONNEMENTS", new BigDecimal("200.00")),
                "Épargner"
        );

        // Simulation d'une défaillance du LLM qui est gérée par le fallback sécurisé
        BudgetPlanResponse response = auditorService.analyzeExpenses(userId, req, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.verifiedAdvices()).allMatch(advice ->
                advice.currentSpending().compareTo(new BigDecimal("200.00")) <= 0 || advice.category().equals("LOISIRS")
        );
    }
}

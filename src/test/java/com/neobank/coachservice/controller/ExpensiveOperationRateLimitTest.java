package com.neobank.coachservice.controller;

import com.neobank.coachservice.client.TransactionServiceClient;
import com.neobank.coachservice.dto.AiExpenseDTOs.WasteAnalysisRequest;
import com.neobank.coachservice.service.AiExpenseAuditorService;
import com.neobank.coachservice.service.SalaryDetectionService;
import com.neobank.coachservice.service.UserRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExpensiveOperationRateLimitTest {

    @Test
    void blocksAiOperationBeforeCallingModelService() {
        AiExpenseAuditorService aiService = mock(AiExpenseAuditorService.class);
        UUID userId = UUID.randomUUID();
        AiExpenseAuditorController controller = new AiExpenseAuditorController(
                aiService, new UserRateLimiter(1, 1)
        );
        Jwt jwt = jwt(userId);
        WasteAnalysisRequest request = new WasteAnalysisRequest(Map.of("LOISIRS", BigDecimal.TEN), null);

        controller.analyzeBudget(jwt, request, new MockHttpServletRequest());
        assertThatThrownBy(() -> controller.analyzeBudget(jwt, request, new MockHttpServletRequest()))
                .isInstanceOf(com.neobank.coachservice.exception.RequestRateLimitExceededException.class);

        verify(aiService, times(1)).analyzeExpenses(eq(userId), eq(request), any());
    }

    @Test
    void blocksSalaryOperationBeforeCallingTransactionService() {
        TransactionServiceClient transactionClient = mock(TransactionServiceClient.class);
        SalaryDetectionService salaryService = mock(SalaryDetectionService.class);
        UUID userId = UUID.randomUUID();
        SalaryDetectionController controller = new SalaryDetectionController(
                transactionClient, salaryService, new UserRateLimiter(1, 1)
        );
        Jwt jwt = jwt(userId);

        controller.detectSalary(jwt);
        assertThatThrownBy(() -> controller.detectSalary(jwt))
                .isInstanceOf(com.neobank.coachservice.exception.RequestRateLimitExceededException.class);

        verify(transactionClient, times(1)).fetchRecentInflows(userId, jwt.getTokenValue());
        verify(salaryService, times(1)).analyzeAndPropose(userId, List.of());
    }

    private static Jwt jwt(UUID userId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", userId.toString())
                .build();
    }
}

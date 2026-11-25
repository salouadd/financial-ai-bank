package com.neobank.coachservice.dto;

import com.neobank.coachservice.dto.AiExpenseDTOs.WasteAnalysisRequest;
import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationRequest;
import com.neobank.coachservice.dto.BudgetProfileDTOs.UpsertProfileRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.CreateVaultRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockIntentRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockConfirmRequest;
import com.neobank.coachservice.controller.RecommendationHistoryController;
import com.neobank.coachservice.repository.BudgetRecommendationRepository;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    @Test
    void acceptsIncomeAtMaximumAndRejectsIncomeAboveItOrWithExtraDecimals() {
        var violationsAtMaximum = validate(new UpsertProfileRequest(
                new BigDecimal("1000000.00"), 50, 30, 20
        ));
        var violationsAboveMaximum = validate(new UpsertProfileRequest(
                new BigDecimal("1000000.01"), 50, 30, 20
        ));
        var violationsWithExtraDecimals = validate(new AllocationRequest(
                new BigDecimal("100.001"), null, null, null
        ));

        assertThat(violationsAtMaximum).isEmpty();
        assertThat(violationsAboveMaximum).isNotEmpty();
        assertThat(violationsWithExtraDecimals).isNotEmpty();
    }

    @Test
    void rejectsExcessiveAiCategoriesAmountsAndGoalText() {
        Map<String, BigDecimal> tooManyCategories = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            tooManyCategories.put("category-" + i, BigDecimal.ONE);
        }
        var tooMany = validate(new WasteAnalysisRequest(tooManyCategories, null));
        var excessiveAmount = validate(new WasteAnalysisRequest(
                Map.of("LOGEMENT", new BigDecimal("1000000.01")), null
        ));
        var longGoal = validate(new WasteAnalysisRequest(
                Map.of("LOGEMENT", BigDecimal.ONE), "x".repeat(161)
        ));

        assertThat(tooMany).isNotEmpty();
        assertThat(excessiveAmount).isNotEmpty();
        assertThat(longGoal).isNotEmpty();
    }

    @Test
    void rejectsOutOfRangeVaultAndLockAmounts() {
        var excessiveVaultGoal = validate(new CreateVaultRequest(
                "Projet", com.neobank.coachservice.model.SmartVault.VaultType.GOAL,
                new BigDecimal("10000000.01"), null
        ));
        var excessiveLockAmount = validate(new LockIntentRequest(new BigDecimal("50000.01")));
        var fractionalLockAmount = validate(new LockIntentRequest(new BigDecimal("1.001")));

        assertThat(excessiveVaultGoal).isNotEmpty();
        assertThat(excessiveLockAmount).isNotEmpty();
        assertThat(fractionalLockAmount).isNotEmpty();
    }

    @Test
    void acceptsOnlySixDigitScaCodes() {
        assertThat(validate(new LockConfirmRequest(java.util.UUID.randomUUID(), "123456"))).isEmpty();
        assertThat(validate(new LockConfirmRequest(java.util.UUID.randomUUID(), "12ab56"))).isNotEmpty();
        assertThat(validate(new LockConfirmRequest(java.util.UUID.randomUUID(), "12345"))).isNotEmpty();
    }

    @Test
    void rejectsNegativeAndExcessiveRecommendationPagination() throws Exception {
        RecommendationHistoryController controller = new RecommendationHistoryController(
                org.mockito.Mockito.mock(BudgetRecommendationRepository.class)
        );
        Method method = RecommendationHistoryController.class.getMethod(
                "listRecommendations",
                org.springframework.security.oauth2.jwt.Jwt.class,
                int.class,
                int.class
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ExecutableValidator executableValidator = factory.getValidator().forExecutables();
            var violations = executableValidator.validateParameters(
                    controller,
                    method,
                    new Object[]{null, -1, 51}
            );
            assertThat(violations).hasSize(2);
        }
    }

    @Test
    void rejectsExpensesWithInvalidCategoryKeys() {
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        expenses.put("x".repeat(41), BigDecimal.ONE);

        assertThat(validate(new WasteAnalysisRequest(expenses, null))).isNotEmpty();
    }

    private static <T> java.util.Set<jakarta.validation.ConstraintViolation<T>> validate(T value) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(value);
        }
    }
}

package com.neobank.coachservice.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class BudgetProfileDTOs {

    public record UpsertProfileRequest(
            @NotNull(message = "Le revenu mensuel net est obligatoire")
            @DecimalMin(value = "0.01", message = "Le revenu net doit être supérieur à 0")
            @DecimalMax(value = "1000000.00", message = "Le revenu net ne peut pas dépasser 1 000 000 MAD")
            @Digits(integer = 7, fraction = 2, message = "Le revenu doit avoir au maximum deux décimales")
            BigDecimal netMonthlyIncome,

            @Min(value = 0, message = "Le ratio Besoins doit être >= 0")
            @Max(value = 100, message = "Le ratio Besoins doit être <= 100")
            int needsRatio,

            @Min(value = 0, message = "Le ratio Envies doit être >= 0")
            @Max(value = 100, message = "Le ratio Envies doit être <= 100")
            int wantsRatio,

            @Min(value = 0, message = "Le ratio Épargne doit être >= 0")
            @Max(value = 100, message = "Le ratio Épargne doit être <= 100")
            int savingsRatio
    ) {
        @AssertTrue(message = "La somme des ratios (Besoins + Envies + Épargne) doit être strictement égale à 100%")
        public boolean isSumHundred() {
            return (needsRatio + wantsRatio + savingsRatio) == 100;
        }
    }

    public record BudgetProfileResponse(
            UUID profileId,
            UUID userId,
            BigDecimal netMonthlyIncome,
            int needsRatio,
            int wantsRatio,
            int savingsRatio,
            Long version,
            Instant lastUpdated
    ) {}
}
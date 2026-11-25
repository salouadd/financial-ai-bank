package com.neobank.coachservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class BudgetAllocationDTOs {

    public record AllocationRequest(
            @NotNull(message = "Le montant net mensuel est obligatoire")
            @DecimalMin(value = "0.01", message = "Le revenu net doit être supérieur à 0")
            @DecimalMax(value = "1000000.00", message = "Le revenu net ne peut pas dépasser 1 000 000 MAD")
            @Digits(integer = 7, fraction = 2, message = "Le revenu doit avoir au maximum deux décimales")
            BigDecimal netMonthlyIncome,

            @Min(value = 0, message = "Le ratio Besoins doit être >= 0")
            @Max(value = 100, message = "Le ratio Besoins doit être <= 100")
            Integer customNeedsRatio,

            @Min(value = 0, message = "Le ratio Envies doit être >= 0")
            @Max(value = 100, message = "Le ratio Envies doit être <= 100")
            Integer customWantsRatio,

            @Min(value = 0, message = "Le ratio Épargne doit être >= 0")
            @Max(value = 100, message = "Le ratio Épargne doit être <= 100")
            Integer customSavingsRatio
    ) {
        @AssertTrue(message = "Si des ratios personnalisés sont fournis, la somme doit être exactement égale à 100%")
        public boolean isCustomRatioSumValid() {
            if (customNeedsRatio == null && customWantsRatio == null && customSavingsRatio == null) {
                return true; // Mode automatique : utilise les ratios du profil
            }
            if (customNeedsRatio != null && customWantsRatio != null && customSavingsRatio != null) {
                return (customNeedsRatio + customWantsRatio + customSavingsRatio) == 100;
            }
            return false; // Ratios incomplets
        }
    }

    public record AllocationResponse(
            BigDecimal netMonthlyIncome,
            BigDecimal needsAllocation,
            BigDecimal wantsAllocation,
            BigDecimal savingsAllocation,
            BigDecimal roundingAdjustment, // Reliquat réinjecté (souvent 0.00 ou 0.01)
            int appliedNeedsRatio,
            int appliedWantsRatio,
            int appliedSavingsRatio
    ) {}
}
package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationRequest;
import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationResponse;
import com.neobank.coachservice.model.BudgetProfile;
import com.neobank.coachservice.repository.BudgetProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class BudgetAllocationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BudgetProfileRepository profileRepository;

    public BudgetAllocationService(BudgetProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public AllocationResponse calculateAllocation(UUID userId, AllocationRequest request) {
        BigDecimal income = request.netMonthlyIncome().setScale(2, RoundingMode.HALF_UP);

        int needsRatio;
        int wantsRatio;
        int savingsRatio;

        // 1. Détermination des ratios : surcharge client ou profil persistant
        if (request.customNeedsRatio() != null) {
            needsRatio = request.customNeedsRatio();
            wantsRatio = request.customWantsRatio();
            savingsRatio = request.customSavingsRatio();
        } else {
            BudgetProfile profile = profileRepository.findByUserId(userId).orElse(null);
            if (profile != null) {
                needsRatio = profile.getNeedsRatio();
                wantsRatio = profile.getWantsRatio();
                savingsRatio = profile.getSavingsRatio();
            } else {
                // Règle par défaut F1/F2
                needsRatio = 50;
                wantsRatio = 30;
                savingsRatio = 20;
            }
        }

        // 2. Calculs préliminaires avec arrondi bancaire HALF_UP
        BigDecimal rawNeeds = income.multiply(BigDecimal.valueOf(needsRatio))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal wants = income.multiply(BigDecimal.valueOf(wantsRatio))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal savings = income.multiply(BigDecimal.valueOf(savingsRatio))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        // 3. Calcul du reliquat comptable : Revenu - (Besoins + Envies + Épargne)
        BigDecimal sumCalculated = rawNeeds.add(wants).add(savings);
        BigDecimal remainder = income.subtract(sumCalculated);

        // 4. Affectation du reliquat aux Besoins (garantie de somme exacte au centime près)
        BigDecimal finalNeeds = rawNeeds.add(remainder);

        return new AllocationResponse(
                income,
                finalNeeds,
                wants,
                savings,
                remainder,
                needsRatio,
                wantsRatio,
                savingsRatio
        );
    }
}
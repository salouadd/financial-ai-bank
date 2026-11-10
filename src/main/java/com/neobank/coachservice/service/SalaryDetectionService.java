package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationRequest;
import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationResponse;
import com.neobank.coachservice.dto.SalaryDetectionDTOs.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalaryDetectionService {

    private final BudgetAllocationService allocationService;

    public SalaryDetectionService(BudgetAllocationService allocationService) {
        this.allocationService = allocationService;
    }

    public SalaryProposalResponse analyzeAndPropose(UUID userId, List<TransactionFeedItem> transactions) {
        // 1. Filtrer uniquement les flux créditeurs significatifs (ex: > 1 500 MAD)
        List<TransactionFeedItem> credits = transactions.stream()
                .filter(t -> "CREDIT".equalsIgnoreCase(t.type()))
                .filter(t -> t.amount().compareTo(new BigDecimal("1500.00")) >= 0)
                .sorted(Comparator.comparing(TransactionFeedItem::executionDate).reversed())
                .toList();

        if (credits.isEmpty()) {
            return new SalaryProposalResponse(
                    false,
                    null,
                    null,
                    "Aucun flux créditeur régulier identifié sur les 90 derniers jours."
            );
        }

        // 2. Regroupement par contrepartie normalisée (source de l'argent)
        Map<String, List<TransactionFeedItem>> groupedBySource = credits.stream()
                .collect(Collectors.groupingBy(t -> normalizeCounterparty(t.counterpartyName())));

        // 3. Identification d'une récurrence mensuelle (écart de 25 à 35 jours)
        for (Map.Entry<String, List<TransactionFeedItem>> entry : groupedBySource.entrySet()) {
            List<TransactionFeedItem> txGroup = entry.getValue();
            if (txGroup.size() >= 2) {
                Optional<DetectedSalaryCandidate> candidate = evaluateRecurrence(entry.getKey(), txGroup);
                if (candidate.isPresent()) {
                    DetectedSalaryCandidate salary = candidate.get();

                    // 4. Génération de l'allocation recommandée (F2) sur la base du salaire détecté
                    AllocationResponse allocation = allocationService.calculateAllocation(
                            userId,
                            new AllocationRequest(salary.estimatedSalary(), null, null, null)
                    );

                    return new SalaryProposalResponse(
                            true,
                            salary,
                            allocation,
                            String.format("Salaire régulier détecté provenant de '%s' (~%s MAD). Souhaitez-vous appliquer votre plan budgétaire ?",
                                    salary.employerOrSource(), salary.estimatedSalary().toPlainString())
                    );
                }
            }
        }

        return new SalaryProposalResponse(
                false,
                null,
                null,
                "Flux entrants analysés : aucune récurrence mensuelle nette constatée."
        );
    }

    private Optional<DetectedSalaryCandidate> evaluateRecurrence(String source, List<TransactionFeedItem> list) {
        List<TransactionFeedItem> sorted = list.stream()
                .sorted(Comparator.comparing(TransactionFeedItem::executionDate))
                .toList();

        long validIntervals = 0;
        BigDecimal sumAmounts = BigDecimal.ZERO;

        for (int i = 0; i < sorted.size() - 1; i++) {
            LocalDate d1 = sorted.get(i).executionDate();
            LocalDate d2 = sorted.get(i + 1).executionDate();
            long daysBetween = ChronoUnit.DAYS.between(d1, d2);

            // Intervalle typique d'un versement mensuel : entre 25 et 35 jours
            if (daysBetween >= 25 && daysBetween <= 35) {
                BigDecimal a1 = sorted.get(i).amount();
                BigDecimal a2 = sorted.get(i + 1).amount();

                // Tolérance de variation maximale de 10% entre deux versements (primes, congés)
                BigDecimal diff = a1.subtract(a2).abs();
                BigDecimal tolerance = a1.multiply(new BigDecimal("0.10"));

                if (diff.compareTo(tolerance) <= 0) {
                    validIntervals++;
                    sumAmounts = sumAmounts.add(a1);
                }
            }
        }

        if (validIntervals >= 1) {
            BigDecimal averageAmount = sumAmounts.divide(BigDecimal.valueOf(validIntervals), 2, RoundingMode.HALF_UP);
            List<LocalDate> dates = sorted.stream().map(TransactionFeedItem::executionDate).toList();
            LocalDate latest = dates.get(dates.size() - 1);

            int confidence = (int) Math.min(100, 70 + (validIntervals * 15));

            return Optional.of(new DetectedSalaryCandidate(source, averageAmount, latest, confidence, dates));
        }

        return Optional.empty();
    }

    private String normalizeCounterparty(String rawName) {
        if (rawName == null) return "SOURCE_INCONNUE";
        return rawName.trim().toUpperCase().replaceAll("[^A-Z0-9 ]", "");
    }
}
package com.neobank.coachservice.dto;

import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SalaryDetectionDTOs {

    // Représente une transaction récupérée via le service central
    public record TransactionFeedItem(
            String transactionId,
            BigDecimal amount,
            String counterpartyName,
            String label,
            LocalDate executionDate,
            String type // CREDIT ou DEBIT
    ) {}

    // Résultat d'une récurrence identifiée
    public record DetectedSalaryCandidate(
            String employerOrSource,
            BigDecimal estimatedSalary,
            LocalDate lastPaymentDate,
            int confidenceScore, // de 0 à 100
            List<LocalDate> historicalDates
    ) {}

    // Proposition finale envoyée au client
    public record SalaryProposalResponse(
            boolean salaryDetected,
            DetectedSalaryCandidate detectedSalary,
            AllocationResponse suggestedAllocation,
            String message
    ) {}
}
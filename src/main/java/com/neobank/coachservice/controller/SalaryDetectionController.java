package com.neobank.coachservice.controller;

import com.neobank.coachservice.client.TransactionServiceClient;
import com.neobank.coachservice.dto.SalaryDetectionDTOs.SalaryProposalResponse;
import com.neobank.coachservice.dto.SalaryDetectionDTOs.TransactionFeedItem;
import com.neobank.coachservice.service.SalaryDetectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coach/salary")
public class SalaryDetectionController {

    private final TransactionServiceClient transactionClient;
    private final SalaryDetectionService detectionService;

    public SalaryDetectionController(
            TransactionServiceClient transactionClient,
            SalaryDetectionService detectionService) {
        this.transactionClient = transactionClient;
        this.detectionService = detectionService;
    }

    @GetMapping("/detect")
    public ResponseEntity<SalaryProposalResponse> detectSalary(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        // Récupération sécurisée des données de flux entrant
        List<TransactionFeedItem> inflows = transactionClient.fetchRecentInflows(userId, jwt.getTokenValue());

        // Analyse et formulation de la proposition d'enveloppes 50/30/20
        SalaryProposalResponse proposal = detectionService.analyzeAndPropose(userId, inflows);

        return ResponseEntity.ok(proposal);
    }
}
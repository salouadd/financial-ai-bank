package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.AiExpenseDTOs.BudgetPlanResponse;
import com.neobank.coachservice.dto.AiExpenseDTOs.WasteAnalysisRequest;
import com.neobank.coachservice.service.AiExpenseAuditorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coach")
public class AiExpenseAuditorController {

    private final AiExpenseAuditorService auditorService;

    public AiExpenseAuditorController(AiExpenseAuditorService auditorService) {
        this.auditorService = auditorService;
    }

    @PostMapping("/analyze-budget")
    public ResponseEntity<BudgetPlanResponse> analyzeBudget(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WasteAnalysisRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = UUID.fromString(jwt.getSubject());
        String clientIp = servletRequest.getRemoteAddr();

        BudgetPlanResponse plan = auditorService.analyzeExpenses(userId, request, clientIp);
        return ResponseEntity.ok(plan);
    }
}
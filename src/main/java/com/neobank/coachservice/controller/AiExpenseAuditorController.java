package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.AiExpenseDTOs.BudgetPlanResponse;
import com.neobank.coachservice.dto.AiExpenseDTOs.WasteAnalysisRequest;
import com.neobank.coachservice.service.AiExpenseAuditorService;
import com.neobank.coachservice.service.UserRateLimiter;
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

import static com.neobank.coachservice.security.JwtPrincipalUtils.userId;

@RestController
@RequestMapping("/api/v1/coach")
public class AiExpenseAuditorController {

    private final AiExpenseAuditorService auditorService;
    private final UserRateLimiter rateLimiter;

    public AiExpenseAuditorController(AiExpenseAuditorService auditorService, UserRateLimiter rateLimiter) {
        this.auditorService = auditorService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/analyze-budget")
    public ResponseEntity<BudgetPlanResponse> analyzeBudget(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WasteAnalysisRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = userId(jwt);
        rateLimiter.checkAiAnalysis(userId);
        String clientIp = servletRequest.getRemoteAddr();

        BudgetPlanResponse plan = auditorService.analyzeExpenses(userId, request, clientIp);
        return ResponseEntity.ok(plan);
    }
}
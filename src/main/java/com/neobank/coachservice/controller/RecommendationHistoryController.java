package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.PrivacyDTOs.RecommendationHistoryItem;
import com.neobank.coachservice.repository.BudgetRecommendationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coach/recommendations")
public class RecommendationHistoryController {

    private final BudgetRecommendationRepository repository;

    public RecommendationHistoryController(BudgetRecommendationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<Page<RecommendationHistoryItem>> listRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID userId = UUID.fromString(jwt.getSubject());
        var pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending());

        Page<RecommendationHistoryItem> history = repository.findAllByUserId(userId, pageable)
                .map(r -> new RecommendationHistoryItem(
                        r.getId(),
                        r.getSummary(),
                        r.getPotentialMonthlySavings(),
                        r.getRecommendationsPayload(),
                        r.getCreatedAt()
                ));

        return ResponseEntity.ok(history);
    }
}
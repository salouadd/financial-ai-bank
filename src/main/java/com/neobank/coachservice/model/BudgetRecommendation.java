package com.neobank.coachservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget_recommendations")
public class BudgetRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "recommendations_payload", nullable = false, columnDefinition = "jsonb")
    private String recommendationsPayload;

    @Column(name = "potential_monthly_savings", nullable = false, precision = 12, scale = 2)
    private BigDecimal potentialMonthlySavings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public BudgetRecommendation() {}

    public BudgetRecommendation(UUID userId, String summary, String recommendationsPayload, BigDecimal potentialMonthlySavings, Instant expiresAt) {
        this.userId = userId;
        this.summary = summary;
        this.recommendationsPayload = recommendationsPayload;
        this.potentialMonthlySavings = potentialMonthlySavings;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSummary() { return summary; }
    public String getRecommendationsPayload() { return recommendationsPayload; }
    public BigDecimal getPotentialMonthlySavings() { return potentialMonthlySavings; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
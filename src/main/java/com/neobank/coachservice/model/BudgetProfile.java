package com.neobank.coachservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budget_profiles")
public class BudgetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "net_monthly_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal netMonthlyIncome;

    @Column(name = "needs_ratio", nullable = false)
    private int needsRatio;

    @Column(name = "wants_ratio", nullable = false)
    private int wantsRatio;

    @Column(name = "savings_ratio", nullable = false)
    private int savingsRatio;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public BudgetProfile() {}

    public BudgetProfile(UUID userId, BigDecimal netMonthlyIncome, int needsRatio, int wantsRatio, int savingsRatio) {
        this.userId = userId;
        this.netMonthlyIncome = netMonthlyIncome;
        this.needsRatio = needsRatio;
        this.wantsRatio = wantsRatio;
        this.savingsRatio = savingsRatio;
        this.updatedAt = Instant.now();
    }

    public void update(BigDecimal netMonthlyIncome, int needsRatio, int wantsRatio, int savingsRatio) {
        this.netMonthlyIncome = netMonthlyIncome;
        this.needsRatio = needsRatio;
        this.wantsRatio = wantsRatio;
        this.savingsRatio = savingsRatio;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getNetMonthlyIncome() { return netMonthlyIncome; }
    public int getNeedsRatio() { return needsRatio; }
    public int getWantsRatio() { return wantsRatio; }
    public int getSavingsRatio() { return savingsRatio; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
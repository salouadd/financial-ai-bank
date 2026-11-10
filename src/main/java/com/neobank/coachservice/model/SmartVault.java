package com.neobank.coachservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "smart_vaults")
public class SmartVault {

    public enum VaultType {
        FIXED_EXPENSES,
        SAVINGS,
        GOAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vault_name", nullable = false, length = 64)
    private String vaultName;

    @Enumerated(EnumType.STRING)
    @Column(name = "vault_type", nullable = false, length = 32)
    private VaultType vaultType;

    @Column(name = "target_amount", precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "auto_transfer_date")
    private Integer autoTransferDate;

    @Column(name = "has_card_linked", nullable = false)
    private boolean hasCardLinked = false;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SmartVault() {}

    public SmartVault(UUID userId, String vaultName, VaultType vaultType, BigDecimal targetAmount, Integer autoTransferDate) {
        this.userId = userId;
        this.vaultName = vaultName;
        this.vaultType = vaultType;
        this.targetAmount = targetAmount;
        this.autoTransferDate = autoTransferDate;
        this.currentBalance = BigDecimal.ZERO;
        this.hasCardLinked = false; // Par défaut et toujours faux pour SAVINGS
        this.updatedAt = Instant.now();
    }

    public void rename(String newName) {
        this.vaultName = newName;
        this.updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        this.currentBalance = this.currentBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        if (this.currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Solde du coffre insuffisant.");
        }
        this.currentBalance = this.currentBalance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getVaultName() { return vaultName; }
    public VaultType getVaultType() { return vaultType; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public Integer getAutoTransferDate() { return autoTransferDate; }
    public boolean isHasCardLinked() { return hasCardLinked; }
    public Long getVersion() { return version; }
}
package com.neobank.coachservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vault_lock_intents")
public class VaultLockIntent {

    public enum IntentStatus {
        PENDING, CONFIRMED, EXPIRED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "sca_challenge", nullable = false, length = 64)
    private String scaChallenge;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IntentStatus status = IntentStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public VaultLockIntent() {}

    public VaultLockIntent(UUID userId, UUID vaultId, BigDecimal amount, String scaChallenge, Instant expiresAt) {
        this.userId = userId;
        this.vaultId = vaultId;
        this.amount = amount;
        this.scaChallenge = scaChallenge;
        this.expiresAt = expiresAt;
        this.status = IntentStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markConfirmed() {
        this.status = IntentStatus.CONFIRMED;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getVaultId() { return vaultId; }
    public BigDecimal getAmount() { return amount; }
    public String getScaChallenge() { return scaChallenge; }
    public IntentStatus getStatus() { return status; }
}
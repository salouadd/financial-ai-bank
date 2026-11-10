package com.neobank.coachservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vault_ledger_entries")
public class VaultLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "intent_id", nullable = false, unique = true)
    private UUID intentId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "entry_type", nullable = false, length = 16)
    private String entryType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public VaultLedgerEntry() {}

    public VaultLedgerEntry(UUID userId, UUID vaultId, UUID intentId, String idempotencyKey, BigDecimal amount, String entryType) {
        this.userId = userId;
        this.vaultId = vaultId;
        this.intentId = intentId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.entryType = entryType;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getEntryType() { return entryType; }
    public Instant getCreatedAt() { return createdAt; }
}
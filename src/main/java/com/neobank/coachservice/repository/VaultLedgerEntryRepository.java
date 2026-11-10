package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.VaultLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultLedgerEntryRepository extends JpaRepository<VaultLedgerEntry, UUID> {
    Optional<VaultLedgerEntry> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
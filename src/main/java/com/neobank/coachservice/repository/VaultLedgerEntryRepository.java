package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.VaultLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface VaultLedgerEntryRepository extends JpaRepository<VaultLedgerEntry, UUID> {
    Optional<VaultLedgerEntry> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
    List<VaultLedgerEntry> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    @Modifying
    @Query(value = """
            UPDATE vault_ledger_entries
            SET user_id = :anonymousUserId,
                idempotency_key = 'erased-' || id::text
            WHERE user_id = :userId
            """, nativeQuery = true)
    int anonymizeUserData(@Param("userId") UUID userId, @Param("anonymousUserId") UUID anonymousUserId);

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:userId as text) || ':' || :idempotencyKey, 0)), 1",
            nativeQuery = true)
    List<Object[]> lockIdempotencyKey(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey);
}
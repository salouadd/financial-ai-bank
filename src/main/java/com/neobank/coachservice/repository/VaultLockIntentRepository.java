package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.VaultLockIntent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultLockIntentRepository extends JpaRepository<VaultLockIntent, UUID> {
    Optional<VaultLockIntent> findByIdAndUserId(UUID id, UUID userId);
    List<VaultLockIntent> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    @Modifying
    @Query(value = """
            UPDATE vault_lock_intents intent
            SET user_id = :anonymousUserId, sca_challenge = 'ERASED'
            WHERE intent.user_id = :userId
              AND EXISTS (
                  SELECT 1 FROM vault_ledger_entries entry
                  WHERE entry.intent_id = intent.id
              )
            """, nativeQuery = true)
    int anonymizeLedgerIntents(
            @Param("userId") UUID userId,
            @Param("anonymousUserId") UUID anonymousUserId);

    @Modifying
    @Query(value = """
            DELETE FROM vault_lock_intents intent
            WHERE intent.user_id = :userId
              AND NOT EXISTS (
                  SELECT 1 FROM vault_ledger_entries entry
                  WHERE entry.intent_id = intent.id
              )
            """, nativeQuery = true)
    int deleteIntentsWithoutLedger(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from VaultLockIntent intent where intent.id = :id and intent.userId = :userId")
    Optional<VaultLockIntent> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId);
}
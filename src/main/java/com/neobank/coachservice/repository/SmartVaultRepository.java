package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.SmartVault;
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
public interface SmartVaultRepository extends JpaRepository<SmartVault, UUID> {

    // Protection anti-IDOR : la requête lie obligatoirement le vaultId ET le userId
    Optional<SmartVault> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vault from SmartVault vault where vault.id = :id and vault.userId = :userId")
    Optional<SmartVault> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    List<SmartVault> findAllByUserId(UUID userId);

    @Modifying
    @Query(value = """
            UPDATE smart_vaults vault
            SET user_id = :anonymousUserId,
                vault_name = 'Archived vault',
                vault_type = 'SAVINGS',
                target_amount = NULL,
                auto_transfer_date = NULL,
                has_card_linked = FALSE,
                updated_at = NOW()
            WHERE vault.user_id = :userId
              AND EXISTS (
                  SELECT 1 FROM vault_ledger_entries entry
                  WHERE entry.vault_id = vault.id
              )
            """, nativeQuery = true)
    int anonymizeVaultsWithLedger(
            @Param("userId") UUID userId,
            @Param("anonymousUserId") UUID anonymousUserId);

    @Modifying
    @Query(value = "DELETE FROM smart_vaults WHERE user_id = :userId", nativeQuery = true)
    int deleteVaultsWithoutLedger(@Param("userId") UUID userId);
}
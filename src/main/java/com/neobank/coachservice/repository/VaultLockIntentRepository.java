package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.VaultLockIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultLockIntentRepository extends JpaRepository<VaultLockIntent, UUID> {
    Optional<VaultLockIntent> findByIdAndUserId(UUID id, UUID userId);
}
package com.neobank.coachservice.repository;

import com.neobank.coachservice.model.SmartVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SmartVaultRepository extends JpaRepository<SmartVault, UUID> {

    // Protection anti-IDOR : la requête lie obligatoirement le vaultId ET le userId
    Optional<SmartVault> findByIdAndUserId(UUID id, UUID userId);

    List<SmartVault> findAllByUserId(UUID userId);
}
package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.SmartVaultDTOs.CreateVaultRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.RenameVaultRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.VaultResponse;
import com.neobank.coachservice.model.AuditLog;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.SmartVaultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SmartVaultService {

    private final SmartVaultRepository vaultRepository;
    private final AuditLogRepository auditLogRepository;

    public SmartVaultService(SmartVaultRepository vaultRepository, AuditLogRepository auditLogRepository) {
        this.vaultRepository = vaultRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public VaultResponse createVault(UUID userId, CreateVaultRequest request, String clientIp) {
        SmartVault vault = new SmartVault(
                userId,
                request.vaultName(),
                request.vaultType(),
                request.targetAmount(),
                request.autoTransferDate()
        );

        SmartVault saved = vaultRepository.save(vault);

        AuditLog audit = new AuditLog(
                userId,
                "CREATE_VAULT",
                "SMART_VAULT",
                saved.getId().toString(),
                String.format("{\"type\":\"%s\",\"name\":\"%s\"}", saved.getVaultType(), saved.getVaultName()),
                clientIp
        );
        auditLogRepository.save(audit);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VaultResponse> listUserVaults(UUID userId) {
        return vaultRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public VaultResponse renameVault(UUID userId, UUID vaultId, RenameVaultRequest request, String clientIp) {
        SmartVault vault = vaultRepository.findByIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Coffre introuvable ou accès non autorisé."));

        vault.rename(request.newVaultName());

        AuditLog audit = new AuditLog(
                userId,
                "RENAME_VAULT",
                "SMART_VAULT",
                vault.getId().toString(),
                String.format("{\"newName\":\"%s\"}", request.newVaultName()),
                clientIp
        );
        auditLogRepository.save(audit);

        return mapToResponse(vault);
    }

    @Transactional
    public void deleteVault(UUID userId, UUID vaultId, String clientIp) {
        SmartVault vault = vaultRepository.findByIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Coffre introuvable ou accès non autorisé."));

        // Règle bancaire stricte : refus de suppression si solde non vide
        if (vault.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Impossible de supprimer un coffre ayant un solde résiduel.");
        }

        vaultRepository.delete(vault);

        AuditLog audit = new AuditLog(
                userId,
                "DELETE_VAULT",
                "SMART_VAULT",
                vaultId.toString(),
                "{}",
                clientIp
        );
        auditLogRepository.save(audit);
    }

    private VaultResponse mapToResponse(SmartVault v) {
        return new VaultResponse(
                v.getId(),
                v.getVaultName(),
                v.getVaultType(),
                v.getTargetAmount(),
                v.getCurrentBalance(),
                v.getAutoTransferDate(),
                v.isHasCardLinked()
        );
    }
}
package com.neobank.coachservice.dto;

import com.neobank.coachservice.model.SmartVault.VaultType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class SmartVaultDTOs {

    public record CreateVaultRequest(
            @NotBlank(message = "Le nom du coffre est obligatoire")
            @Size(min = 2, max = 64, message = "Le nom doit comporter entre 2 et 64 caractères")
            String vaultName,

            @NotNull(message = "Le type de coffre est obligatoire")
            VaultType vaultType,

            @DecimalMin(value = "0.01", message = "L'objectif d'épargne doit être supérieur à 0")
            BigDecimal targetAmount,

            @Min(value = 1, message = "Le jour de transfert doit être >= 1")
            @Max(value = 31, message = "Le jour de transfert doit être <= 31")
            Integer autoTransferDate
    ) {}

    public record RenameVaultRequest(
            @NotBlank(message = "Le nouveau nom est obligatoire")
            @Size(min = 2, max = 64, message = "Le nom doit comporter entre 2 et 64 caractères")
            String newVaultName
    ) {}

    public record VaultResponse(
            UUID id,
            String vaultName,
            VaultType vaultType,
            BigDecimal targetAmount,
            BigDecimal currentBalance,
            Integer autoTransferDate,
            boolean hasCardLinked
    ) {}
}
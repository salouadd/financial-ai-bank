package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.SmartVaultDTOs.CreateVaultRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.RenameVaultRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.VaultResponse;
import com.neobank.coachservice.service.SmartVaultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.neobank.coachservice.security.JwtPrincipalUtils.userId;

@RestController
@RequestMapping("/api/v1/coach/vaults")
public class SmartVaultController {

    private final SmartVaultService vaultService;

    public SmartVaultController(SmartVaultService vaultService) {
        this.vaultService = vaultService;
    }

    @PostMapping
    public ResponseEntity<VaultResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVaultRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = userId(jwt);
        String clientIp = servletRequest.getRemoteAddr();

        VaultResponse response = vaultService.createVault(userId, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<VaultResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return ResponseEntity.ok(vaultService.listUserVaults(userId));
    }

    @PatchMapping("/{vaultId}")
    public ResponseEntity<VaultResponse> rename(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vaultId,
            @Valid @RequestBody RenameVaultRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = userId(jwt);
        String clientIp = servletRequest.getRemoteAddr();

        VaultResponse response = vaultService.renameVault(userId, vaultId, request, clientIp);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{vaultId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vaultId,
            HttpServletRequest servletRequest) {

        UUID userId = userId(jwt);
        String clientIp = servletRequest.getRemoteAddr();

        vaultService.deleteVault(userId, vaultId, clientIp);
        return ResponseEntity.noContent().build();
    }
}
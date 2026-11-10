package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.VaultLockDTOs.*;
import com.neobank.coachservice.service.VaultLockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coach/vaults/{vaultId}")
public class VaultLockController {

    private final VaultLockService lockService;

    public VaultLockController(VaultLockService lockService) {
        this.lockService = lockService;
    }

    @PostMapping("/lock-intent")
    public ResponseEntity<LockIntentResponse> createIntent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vaultId,
            @Valid @RequestBody LockIntentRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = UUID.fromString(jwt.getSubject());
        String ip = servletRequest.getRemoteAddr();

        LockIntentResponse response = lockService.createLockIntent(userId, vaultId, request, ip);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/lock-confirm")
    public ResponseEntity<LockConfirmResponse> confirmLock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vaultId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody LockConfirmRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = UUID.fromString(jwt.getSubject());
        String ip = servletRequest.getRemoteAddr();

        LockConfirmResponse response = lockService.confirmLock(userId, vaultId, idempotencyKey, request, ip);
        return ResponseEntity.ok(response);
    }
}
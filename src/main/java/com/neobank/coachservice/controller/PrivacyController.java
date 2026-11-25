package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.PrivacyDTOs.*;
import com.neobank.coachservice.service.PrivacyManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.neobank.coachservice.security.JwtPrincipalUtils.userId;

@RestController
@RequestMapping("/api/v1/coach/me")
public class PrivacyController {

    private final PrivacyManagementService privacyService;

    public PrivacyController(PrivacyManagementService privacyService) {
        this.privacyService = privacyService;
    }

    @GetMapping("/consent")
    public ResponseEntity<ConsentStatusResponse> getConsent(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return ResponseEntity.ok(privacyService.getConsentStatus(userId));
    }

    @PutMapping("/consent")
    public ResponseEntity<ConsentStatusResponse> updateConsent(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateConsentRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = userId(jwt);
        String ip = servletRequest.getRemoteAddr();
        return ResponseEntity.ok(privacyService.updateConsent(userId, request, ip));
    }

    @GetMapping("/export")
    public ResponseEntity<UserDataExportResponse> exportData(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return ResponseEntity.ok(privacyService.exportUserData(userId));
    }

    @DeleteMapping("/data")
    public ResponseEntity<Void> eraseData(
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = userId(jwt);
        privacyService.deleteUserData(userId);
        return ResponseEntity.noContent().build();
    }
}
package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.BudgetProfileDTOs.BudgetProfileResponse;
import com.neobank.coachservice.dto.BudgetProfileDTOs.UpsertProfileRequest;
import com.neobank.coachservice.service.BudgetProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.neobank.coachservice.security.JwtPrincipalUtils.userId;

@RestController
@RequestMapping("/api/v1/coach/profile")
public class BudgetProfileController {

    private final BudgetProfileService profileService;

    public BudgetProfileController(BudgetProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<BudgetProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PutMapping
    public ResponseEntity<BudgetProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertProfileRequest request,
            HttpServletRequest servletRequest) {

        UUID userId = userId(jwt);
        String clientIp = servletRequest.getRemoteAddr();

        BudgetProfileResponse response = profileService.upsertProfile(userId, request, clientIp);
        return ResponseEntity.ok(response);
    }
}
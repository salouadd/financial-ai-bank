package com.neobank.coachservice.controller;

import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationRequest;
import com.neobank.coachservice.dto.BudgetAllocationDTOs.AllocationResponse;
import com.neobank.coachservice.service.BudgetAllocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coach/allocate")
public class BudgetAllocationController {

    private final BudgetAllocationService allocationService;

    public BudgetAllocationController(BudgetAllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping
    public ResponseEntity<AllocationResponse> computeAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AllocationRequest request) {

        UUID userId = UUID.fromString(jwt.getSubject());
        AllocationResponse response = allocationService.calculateAllocation(userId, request);
        return ResponseEntity.ok(response);
    }
}
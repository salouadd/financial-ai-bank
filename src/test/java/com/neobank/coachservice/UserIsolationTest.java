package com.neobank.coachservice;

import com.neobank.coachservice.controller.*;
import com.neobank.coachservice.dto.BudgetProfileDTOs.UpsertProfileRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.CreateVaultRequest;
import com.neobank.coachservice.dto.SmartVaultDTOs.RenameVaultRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockConfirmRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockIntentRequest;
import com.neobank.coachservice.model.SmartVault.VaultType;
import com.neobank.coachservice.repository.*;
import com.neobank.coachservice.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserIsolationTest {

    private final UUID authenticatedUserId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @Test
    void profileReadsAndUpdatesUseSubjectInsteadOfConflictingUserIdClaim() {
        BudgetProfileService service = mock(BudgetProfileService.class);
        BudgetProfileController controller = new BudgetProfileController(service);
        Jwt jwt = jwtFor(authenticatedUserId, otherUserId);
        UpsertProfileRequest request = new UpsertProfileRequest(new BigDecimal("5000"), 50, 30, 20);

        controller.getProfile(jwt);
        controller.updateProfile(jwt, request, new MockHttpServletRequest());

        verify(service).getProfile(authenticatedUserId);
        verify(service).upsertProfile(eq(authenticatedUserId), eq(request), any());
        verify(service, never()).getProfile(otherUserId);
        verify(service, never()).upsertProfile(eq(otherUserId), any(), any());
    }

    @Test
    void everyVaultOperationUsesSubjectInsteadOfConflictingUserIdClaim() {
        SmartVaultService service = mock(SmartVaultService.class);
        SmartVaultController controller = new SmartVaultController(service);
        Jwt jwt = jwtFor(authenticatedUserId, otherUserId);
        UUID vaultId = UUID.randomUUID();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        controller.create(jwt, new CreateVaultRequest("Épargne", VaultType.SAVINGS, null, null), servletRequest);
        controller.list(jwt);
        controller.rename(jwt, vaultId, new RenameVaultRequest("Projet"), servletRequest);
        controller.delete(jwt, vaultId, servletRequest);

        verify(service).createVault(eq(authenticatedUserId), any(), any());
        verify(service).listUserVaults(authenticatedUserId);
        verify(service).renameVault(eq(authenticatedUserId), eq(vaultId), any(), any());
        verify(service).deleteVault(authenticatedUserId, vaultId, servletRequest.getRemoteAddr());
        verify(service, never()).listUserVaults(otherUserId);
    }

    @Test
    void recommendationHistoryIsQueriedOnlyForAuthenticatedSubject() {
        BudgetRecommendationRepository repository = mock(BudgetRecommendationRepository.class);
        when(repository.findAllByUserId(eq(authenticatedUserId), any())).thenReturn(Page.empty());
        RecommendationHistoryController controller = new RecommendationHistoryController(repository);

        controller.listRecommendations(jwtFor(authenticatedUserId, otherUserId), 0, 10);

        verify(repository).findAllByUserId(eq(authenticatedUserId), any());
        verify(repository, never()).findAllByUserId(eq(otherUserId), any());
    }

    @Test
    void privacyExportAndErasureUseAuthenticatedSubject() {
        PrivacyManagementService service = mock(PrivacyManagementService.class);
        PrivacyController controller = new PrivacyController(service);
        Jwt jwt = jwtFor(authenticatedUserId, otherUserId);

        controller.exportData(jwt);
        controller.eraseData(jwt);

        verify(service).exportUserData(authenticatedUserId);
        verify(service).deleteUserData(authenticatedUserId);
        verify(service, never()).exportUserData(otherUserId);
        verify(service, never()).deleteUserData(otherUserId);
    }

    @Test
    void lockIntentAndConfirmationUseAuthenticatedSubject() {
        VaultLockService service = mock(VaultLockService.class);
        VaultLockController controller = new VaultLockController(service);
        Jwt jwt = jwtFor(authenticatedUserId, otherUserId);
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        controller.createIntent(jwt, vaultId, new LockIntentRequest(new BigDecimal("100")), servletRequest);
        controller.confirmLock(
                jwt,
                vaultId,
                "idempotency-key",
                new LockConfirmRequest(intentId, "123456"),
                servletRequest
        );

        verify(service).createLockIntent(eq(authenticatedUserId), eq(vaultId), any(), any());
        verify(service).confirmLock(eq(authenticatedUserId), eq(vaultId), eq("idempotency-key"), any(), any());
        verify(service, never()).createLockIntent(eq(otherUserId), any(), any(), any());
        verify(service, never()).confirmLock(eq(otherUserId), any(), any(), any(), any());
    }

    @Test
    void vaultOperationsRejectResourcesNotOwnedByAuthenticatedUser() {
        SmartVaultRepository vaultRepository = mock(SmartVaultRepository.class);
        SmartVaultService service = new SmartVaultService(vaultRepository, mock(AuditLogRepository.class));
        UUID vaultId = UUID.randomUUID();
        when(vaultRepository.findByIdAndUserId(vaultId, authenticatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renameVault(
                authenticatedUserId, vaultId, new RenameVaultRequest("Nouveau nom"), "127.0.0.1"
        )).isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Coffre introuvable ou accès non autorisé.");
        assertThatThrownBy(() -> service.deleteVault(authenticatedUserId, vaultId, "127.0.0.1"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Coffre introuvable ou accès non autorisé.");

        verify(vaultRepository, times(2)).findByIdAndUserId(vaultId, authenticatedUserId);
        verify(vaultRepository, never()).delete(any());
    }

    @Test
    void lockConfirmationRejectsIntentOwnedByAnotherUser() {
        VaultLedgerEntryRepository ledgerRepository = mock(VaultLedgerEntryRepository.class);
        VaultLockIntentRepository intentRepository = mock(VaultLockIntentRepository.class);
        VaultLockService service = new VaultLockService(
                mock(SmartVaultRepository.class),
                intentRepository,
                ledgerRepository,
                mock(AuditLogRepository.class),
                mock(com.neobank.coachservice.sca.ScaProvider.class)
        );
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        when(ledgerRepository.findByUserIdAndIdempotencyKey(authenticatedUserId, "idempotency-key"))
                .thenReturn(Optional.empty());
        when(intentRepository.findByIdAndUserId(intentId, authenticatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmLock(
                authenticatedUserId,
                vaultId,
                "idempotency-key",
                new LockConfirmRequest(intentId, "123456"),
                "127.0.0.1"
        )).isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Intention de verrouillage expirée ou invalide.");

        verify(intentRepository).findByIdAndUserIdForUpdate(intentId, authenticatedUserId);
        verify(intentRepository, never()).save(any());
    }

    private Jwt jwtFor(UUID subject, UUID userIdClaim) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", subject.toString())
                .claim("userId", userIdClaim.toString())
                .build();
    }
}

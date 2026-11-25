package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.VaultLockDTOs.LockConfirmRequest;
import com.neobank.coachservice.dto.VaultLockDTOs.LockIntentRequest;
import com.neobank.coachservice.exception.ScaVerificationFailedException;
import com.neobank.coachservice.exception.ScaAttemptsExceededException;
import com.neobank.coachservice.exception.ScaChallengeExpiredException;
import com.neobank.coachservice.exception.IdempotencyKeyConflictException;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.model.VaultLedgerEntry;
import com.neobank.coachservice.model.VaultLockIntent;
import com.neobank.coachservice.repository.AuditLogRepository;
import com.neobank.coachservice.repository.SmartVaultRepository;
import com.neobank.coachservice.repository.VaultLedgerEntryRepository;
import com.neobank.coachservice.repository.VaultLockIntentRepository;
import com.neobank.coachservice.sca.DisabledScaProvider;
import com.neobank.coachservice.sca.ScaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VaultLockServiceTest {

    private SmartVaultRepository vaultRepository;
    private VaultLockIntentRepository intentRepository;
    private VaultLedgerEntryRepository ledgerRepository;
    private AuditLogRepository auditLogRepository;
    private ScaProvider scaProvider;
    private VaultLockService service;

    @BeforeEach
    void setUp() {
        vaultRepository = mock(SmartVaultRepository.class);
        intentRepository = mock(VaultLockIntentRepository.class);
        ledgerRepository = mock(VaultLedgerEntryRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        scaProvider = mock(ScaProvider.class);
        service = new VaultLockService(
                vaultRepository, intentRepository, ledgerRepository, auditLogRepository, scaProvider);
    }

    @Test
    void creationRequestsProviderChallengeBoundToUserVaultAndAmount() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("125.50");
        when(vaultRepository.findByIdAndUserId(vaultId, userId))
                .thenReturn(Optional.of(newVault(userId)));
        when(intentRepository.save(any(VaultLockIntent.class))).thenAnswer(invocation -> {
            VaultLockIntent intent = invocation.getArgument(0);
            ReflectionTestUtils.setField(intent, "id", intentId);
            return intent;
        });
        when(scaProvider.issueChallenge(any())).thenReturn("provider-challenge-id");

        var response = service.createLockIntent(
                userId, vaultId, new LockIntentRequest(amount), "127.0.0.1");

        assertThat(response.intentId()).isEqualTo(intentId);
        assertThat(response.scaChallengeMethod()).isEqualTo("PROVIDER_OTP");
        var challenge = org.mockito.ArgumentCaptor.forClass(ScaProvider.ChallengeRequest.class);
        verify(scaProvider).issueChallenge(challenge.capture());
        assertThat(challenge.getValue().userId()).isEqualTo(userId);
        assertThat(challenge.getValue().intentId()).isEqualTo(intentId);
        assertThat(challenge.getValue().vaultId()).isEqualTo(vaultId);
        assertThat(challenge.getValue().amount()).isEqualByComparingTo(amount);
        assertThat(challenge.getValue().expiresAt()).isEqualTo(response.expiresAt());
    }

    @Test
    void missingProviderFailsClosedAndConfirmationLookupUsesWriteLock() throws Exception {
        DisabledScaProvider disabledProvider = new DisabledScaProvider();
        assertThatThrownBy(() -> disabledProvider.issueChallenge(new ScaProvider.ChallengeRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ONE, Instant.now().plusSeconds(60))))
                .isInstanceOf(com.neobank.coachservice.sca.ScaProviderUnavailableException.class);

        var lookup = VaultLockIntentRepository.class.getMethod(
                "findByIdAndUserIdForUpdate", UUID.class, UUID.class);
        assertThat(lookup.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void declinedCodeConsumesAttemptAndNeverCreditsVault() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        VaultLockIntent intent = new VaultLockIntent(
                userId, vaultId, new BigDecimal("25.00"), "provider-challenge-id",
                Instant.now().plusSeconds(120));
        ReflectionTestUtils.setField(intent, "id", intentId);
        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "key"))
                .thenReturn(Optional.empty());
        when(intentRepository.findByIdAndUserIdForUpdate(intentId, userId))
                .thenReturn(Optional.of(intent));
        when(scaProvider.verifyChallenge(any())).thenReturn(false);

        assertThatThrownBy(() -> service.confirmLock(
                userId, vaultId, "key", new LockConfirmRequest(intentId, "123456"), "127.0.0.1"))
                .isInstanceOf(ScaVerificationFailedException.class);

        assertThat(intent.getFailedAttempts()).isEqualTo(1);
        verify(intentRepository).save(intent);
        verify(vaultRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void expiredChallengeIsMarkedAndNeverSentToProvider() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        VaultLockIntent intent = new VaultLockIntent(
                userId, vaultId, new BigDecimal("25.00"), "provider-challenge-id",
                Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(intent, "id", intentId);
        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "key"))
                .thenReturn(Optional.empty());
        when(intentRepository.findByIdAndUserIdForUpdate(intentId, userId))
                .thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.confirmLock(
                userId, vaultId, "key", new LockConfirmRequest(intentId, "123456"), "127.0.0.1"))
                .isInstanceOf(ScaChallengeExpiredException.class);

        assertThat(intent.getStatus()).isEqualTo(VaultLockIntent.IntentStatus.EXPIRED);
        verify(scaProvider, never()).verifyChallenge(any());
        verify(vaultRepository, never()).save(any());
    }

    @Test
    void locksIntentAfterFiveDeclinedCodesAndDoesNotCallProviderAgain() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        VaultLockIntent intent = new VaultLockIntent(
                userId, vaultId, new BigDecimal("25.00"), "provider-challenge-id",
                Instant.now().plusSeconds(120));
        ReflectionTestUtils.setField(intent, "id", intentId);
        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "key"))
                .thenReturn(Optional.empty());
        when(intentRepository.findByIdAndUserIdForUpdate(intentId, userId))
                .thenReturn(Optional.of(intent));
        when(scaProvider.verifyChallenge(any())).thenReturn(false);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> service.confirmLock(
                    userId, vaultId, "key", new LockConfirmRequest(intentId, "123456"), "127.0.0.1"))
                    .isInstanceOf(ScaVerificationFailedException.class);
        }
        assertThatThrownBy(() -> service.confirmLock(
                userId, vaultId, "key", new LockConfirmRequest(intentId, "123456"), "127.0.0.1"))
                .isInstanceOf(ScaAttemptsExceededException.class);

        assertThat(intent.getFailedAttempts()).isEqualTo(5);
        assertThat(intent.getStatus()).isEqualTo(VaultLockIntent.IntentStatus.FAILED);
        verify(scaProvider, times(5)).verifyChallenge(any());
        verify(vaultRepository, never()).save(any());
    }

    @Test
    void acceptedProviderVerificationCreditsOnlyTheBoundIntentAmount() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("25.00");
        VaultLockIntent intent = new VaultLockIntent(
                userId, vaultId, amount, "provider-challenge-id", Instant.now().plusSeconds(120));
        ReflectionTestUtils.setField(intent, "id", intentId);
        SmartVault vault = newVault(userId);
        VaultLedgerEntry ledgerEntry = mock(VaultLedgerEntry.class);
        when(ledgerEntry.getId()).thenReturn(UUID.randomUUID());
        when(ledgerEntry.getCreatedAt()).thenReturn(Instant.now());
        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "key"))
                .thenReturn(Optional.empty());
        when(intentRepository.findByIdAndUserIdForUpdate(intentId, userId))
                .thenReturn(Optional.of(intent));
        when(vaultRepository.findByIdAndUserIdForUpdate(vaultId, userId)).thenReturn(Optional.of(vault));
        when(ledgerRepository.save(any(VaultLedgerEntry.class))).thenReturn(ledgerEntry);
        when(scaProvider.verifyChallenge(any())).thenReturn(true);

        var response = service.confirmLock(
                userId, vaultId, "key", new LockConfirmRequest(intentId, "123456"), "127.0.0.1");

        assertThat(response.transferredAmount()).isEqualByComparingTo(amount);
        assertThat(vault.getCurrentBalance()).isEqualByComparingTo(amount);
        assertThat(intent.getStatus()).isEqualTo(VaultLockIntent.IntentStatus.CONFIRMED);
        var verification = org.mockito.ArgumentCaptor.forClass(ScaProvider.VerificationRequest.class);
        verify(scaProvider).verifyChallenge(verification.capture());
        assertThat(verification.getValue().challengeId()).isEqualTo("provider-challenge-id");
        assertThat(verification.getValue().userId()).isEqualTo(userId);
        assertThat(verification.getValue().intentId()).isEqualTo(intentId);
        assertThat(verification.getValue().vaultId()).isEqualTo(vaultId);
        assertThat(verification.getValue().amount()).isEqualByComparingTo(amount);
        assertThat(verification.getValue().code()).isEqualTo("123456");

        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "another-key"))
                .thenReturn(Optional.empty());
        when(intentRepository.findByIdAndUserIdForUpdate(intentId, userId))
                .thenReturn(Optional.of(intent));
        assertThatThrownBy(() -> service.confirmLock(
                userId, vaultId, "another-key", new LockConfirmRequest(intentId, "123456"), "127.0.0.1"))
                .isInstanceOf(ScaChallengeExpiredException.class);
        verify(scaProvider, times(1)).verifyChallenge(any());
    }

    @Test
    void idempotencyReplayMustMatchTheOriginalOperation() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID otherVaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        VaultLedgerEntry entry = ledgerEntry(userId, vaultId, intentId, "same-key");
        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "same-key"))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.confirmLock(
                userId, otherVaultId, "same-key",
                new LockConfirmRequest(intentId, "123456"), "127.0.0.1"))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(vaultRepository, never()).findByIdAndUserId(any(), any());
        verify(scaProvider, never()).verifyChallenge(any());
        verify(vaultRepository, never()).save(any());
    }

    @Test
    void exactIdempotencyReplayReturnsOriginalTransactionWithoutSca() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        VaultLedgerEntry entry = ledgerEntry(userId, vaultId, intentId, "same-key");
        when(entry.getId()).thenReturn(transactionId);
        SmartVault vault = newVault(userId);
        when(ledgerRepository.findByUserIdAndIdempotencyKey(userId, "same-key"))
                .thenReturn(Optional.of(entry));
        when(vaultRepository.findByIdAndUserId(vaultId, userId)).thenReturn(Optional.of(vault));

        var response = service.confirmLock(
                userId, vaultId, "same-key",
                new LockConfirmRequest(intentId, "123456"), "127.0.0.1");

        assertThat(response.transactionId()).isEqualTo(transactionId);
        assertThat(response.status()).isEqualTo("COMPLETED_IDEMPOTENT_REPLAY");
        verify(scaProvider, never()).verifyChallenge(any());
        verify(vaultRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void confirmationLocksTheVaultAndSerializesTheUserIdempotencyKey() throws Exception {
        var vaultLookup = SmartVaultRepository.class.getMethod(
                "findByIdAndUserIdForUpdate", UUID.class, UUID.class);
        assertThat(vaultLookup.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);

        var keyLock = VaultLedgerEntryRepository.class.getMethod(
                "lockIdempotencyKey", UUID.class, String.class);
        assertThat(keyLock.getAnnotation(org.springframework.data.jpa.repository.Query.class).value())
                .contains("pg_advisory_xact_lock");
    }

    private VaultLedgerEntry ledgerEntry(UUID userId, UUID vaultId, UUID intentId, String idempotencyKey) {
        VaultLedgerEntry entry = mock(VaultLedgerEntry.class);
        when(entry.getUserId()).thenReturn(userId);
        when(entry.getVaultId()).thenReturn(vaultId);
        when(entry.getIntentId()).thenReturn(intentId);
        when(entry.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(entry.getAmount()).thenReturn(new BigDecimal("25.00"));
        when(entry.getEntryType()).thenReturn("LOCK_INFLOW");
        when(entry.getCreatedAt()).thenReturn(Instant.now());
        when(entry.getId()).thenReturn(UUID.randomUUID());
        return entry;
    }

    private SmartVault newVault(UUID userId) {
        return new SmartVault(
                userId, "Épargne", SmartVault.VaultType.SAVINGS, new BigDecimal("1000.00"), null);
    }
}

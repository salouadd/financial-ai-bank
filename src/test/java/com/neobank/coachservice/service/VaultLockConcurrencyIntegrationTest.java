package com.neobank.coachservice.service;

import com.neobank.coachservice.dto.VaultLockDTOs.LockConfirmRequest;
import com.neobank.coachservice.model.SmartVault;
import com.neobank.coachservice.model.VaultLockIntent;
import com.neobank.coachservice.repository.SmartVaultRepository;
import com.neobank.coachservice.repository.VaultLedgerEntryRepository;
import com.neobank.coachservice.repository.VaultLockIntentRepository;
import com.neobank.coachservice.sca.ScaProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "security.jwt.issuer=https://identity.example.test",
        "security.jwt.audience=financial-coach-service"
})
class VaultLockConcurrencyIntegrationTest {

    @Autowired
    private VaultLockService vaultLockService;

    @Autowired
    private SmartVaultRepository vaultRepository;

    @Autowired
    private VaultLockIntentRepository intentRepository;

    @Autowired
    private VaultLedgerEntryRepository ledgerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ScaProvider scaProvider;

    @Test
    void simultaneousConfirmationsWithSameKeyCreditVaultAndLedgerOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID vaultId;
        UUID intentId;
        String idempotencyKey = "concurrency-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("125.50");

        SmartVault vault = vaultRepository.saveAndFlush(new SmartVault(
                userId, "Concurrency test", SmartVault.VaultType.SAVINGS, new BigDecimal("1000.00"), null));
        vaultId = vault.getId();
        VaultLockIntent intent = intentRepository.saveAndFlush(new VaultLockIntent(
                userId, vaultId, amount, "concurrency-challenge", Instant.now().plusSeconds(120)));
        intentId = intent.getId();
        when(scaProvider.verifyChallenge(any())).thenReturn(true);

        try {
            LockConfirmRequest request = new LockConfirmRequest(intentId, "123456");
            CompletableFuture<?> first = CompletableFuture.supplyAsync(() ->
                    vaultLockService.confirmLock(userId, vaultId, idempotencyKey, request, "127.0.0.1"));
            CompletableFuture<?> second = CompletableFuture.supplyAsync(() ->
                    vaultLockService.confirmLock(userId, vaultId, idempotencyKey, request, "127.0.0.1"));

            CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

            assertThat(vaultRepository.findByIdAndUserId(vaultId, userId))
                    .get()
                    .extracting(SmartVault::getCurrentBalance)
                    .isEqualTo(new BigDecimal("125.50"));
            assertThat(ledgerRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
                    .isPresent()
                    .get()
                    .extracting(entry -> entry.getIntentId())
                    .isEqualTo(intentId);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from vault_ledger_entries where user_id = ? and idempotency_key = ?",
                    Integer.class, userId, idempotencyKey)).isEqualTo(1);
            verify(scaProvider, times(1)).verifyChallenge(any());
        } finally {
            jdbcTemplate.update("delete from audit_logs where user_id = ?", userId);
            jdbcTemplate.update("delete from vault_ledger_entries where user_id = ?", userId);
            jdbcTemplate.update("delete from vault_lock_intents where user_id = ?", userId);
            jdbcTemplate.update("delete from smart_vaults where user_id = ?", userId);
        }
    }
}

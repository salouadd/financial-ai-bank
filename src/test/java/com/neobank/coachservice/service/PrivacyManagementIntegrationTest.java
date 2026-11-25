package com.neobank.coachservice.service;

import com.neobank.coachservice.exception.DataErasureBlockedException;
import com.neobank.coachservice.sca.ScaProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "security.jwt.issuer=https://identity.example.test",
        "security.jwt.audience=financial-coach-service"
})
class PrivacyManagementIntegrationTest {

    @Autowired
    private PrivacyManagementService privacyManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ScaProvider scaProvider;

    private UUID userId;
    private UUID vaultId;
    private UUID intentId;
    private UUID ledgerEntryId;
    private UUID profileId;
    private UUID consentId;
    private UUID recommendationId;
    private UUID transientVaultId;
    private UUID transientIntentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        intentId = UUID.randomUUID();
        ledgerEntryId = UUID.randomUUID();
        profileId = UUID.randomUUID();
        consentId = UUID.randomUUID();
        recommendationId = UUID.randomUUID();
        transientVaultId = UUID.randomUUID();
        transientIntentId = UUID.randomUUID();
    }

    @AfterEach
    void cleanUp() {
        UUID retainedUserId = jdbcTemplate.query(
                "select user_id from vault_ledger_entries where id = ?",
                resultSet -> resultSet.next() ? resultSet.getObject(1, UUID.class) : null,
                ledgerEntryId);
        UUID auditUserId = retainedUserId == null ? userId : retainedUserId;
        jdbcTemplate.update("delete from audit_logs where user_id in (?, ?)", userId, auditUserId);
        jdbcTemplate.update("delete from vault_ledger_entries where id = ?", ledgerEntryId);
        jdbcTemplate.update("delete from vault_lock_intents where id = ?", intentId);
        jdbcTemplate.update("delete from vault_lock_intents where id = ?", transientIntentId);
        jdbcTemplate.update("delete from smart_vaults where id = ?", vaultId);
        jdbcTemplate.update("delete from smart_vaults where id = ?", transientVaultId);
        jdbcTemplate.update("delete from budget_recommendations where id = ?", recommendationId);
        jdbcTemplate.update("delete from budget_profiles where id = ?", profileId);
        jdbcTemplate.update("delete from user_consents where id = ?", consentId);
    }

    @Test
    void exportIncludesPersonalAndFinancialRecordsAndErasureAnonymizesRetainedLedger() {
        insertUserRecords(BigDecimal.ZERO, true);

        var export = privacyManagementService.exportUserData(userId);

        assertThat(export.consent()).isNotNull();
        assertThat(export.consent().clientIp()).isEqualTo("192.0.2.10");
        assertThat(export.profile().netMonthlyIncome()).isEqualByComparingTo("5000.00");
        assertThat(export.vaults()).hasSize(2);
        assertThat(export.recommendationsHistory()).hasSize(1);
        assertThat(export.lockIntents()).hasSize(2);
        assertThat(export.ledgerEntries()).hasSize(1);
        assertThat(export.auditLogs()).hasSize(1);

        privacyManagementService.deleteUserData(userId);

        UUID anonymousUserId = jdbcTemplate.queryForObject(
                "select user_id from vault_ledger_entries where id = ?",
                UUID.class,
                ledgerEntryId);
        assertThat(anonymousUserId).isNotEqualTo(userId);
        assertThat(jdbcTemplate.queryForObject(
                "select idempotency_key from vault_ledger_entries where id = ?",
                String.class,
                ledgerEntryId)).startsWith("erased-");
        assertThat(jdbcTemplate.queryForObject(
                "select user_id from vault_lock_intents where id = ?",
                UUID.class,
                intentId)).isEqualTo(anonymousUserId);
        assertThat(jdbcTemplate.queryForObject(
                "select sca_challenge from vault_lock_intents where id = ?",
                String.class,
                intentId)).isEqualTo("ERASED");
        assertThat(jdbcTemplate.queryForObject(
                "select vault_name from smart_vaults where id = ?",
                String.class,
                vaultId)).isEqualTo("Archived vault");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from smart_vaults where id = ?",
                Integer.class,
                transientVaultId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from vault_lock_intents where id = ?",
                Integer.class,
                transientIntentId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from budget_profiles where user_id = ?",
                Integer.class,
                userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from budget_recommendations where user_id = ?",
                Integer.class,
                userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_consents where user_id = ?",
                Integer.class,
                userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where user_id = ?",
                Integer.class,
                userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where user_id = ? and action = 'DATA_ERASURE_COMPLETED' and ip_address is null",
                Integer.class,
                anonymousUserId)).isEqualTo(1);
    }

    @Test
    void erasureWithPositiveVaultBalanceIsRejectedWithoutDeletingData() {
        insertUserRecords(new BigDecimal("25.00"), false);

        assertThatThrownBy(() -> privacyManagementService.deleteUserData(userId))
                .isInstanceOf(DataErasureBlockedException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from smart_vaults where id = ?",
                Integer.class,
                vaultId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from budget_profiles where id = ?",
                Integer.class,
                profileId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_consents where id = ?",
                Integer.class,
                consentId)).isEqualTo(1);
    }

    private void insertUserRecords(BigDecimal balance, boolean withLedger) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);

        jdbcTemplate.update("""
                insert into smart_vaults
                    (id, user_id, vault_name, vault_type, current_balance, has_card_linked, created_at, updated_at)
                values (?, ?, 'Personal savings', 'SAVINGS', ?, false, ?, ?)
                """, vaultId, userId, balance, timestamp, timestamp);

        jdbcTemplate.update("""
                insert into budget_profiles
                    (id, user_id, net_monthly_income, needs_ratio, wants_ratio, savings_ratio, created_at, updated_at)
                values (?, ?, 5000.00, 50, 30, 20, ?, ?)
                """, profileId, userId, timestamp, timestamp);

        jdbcTemplate.update("""
                insert into user_consents
                    (id, user_id, ai_processing_consented, policy_version, consented_at, ip_address, updated_at)
                values (?, ?, true, 'policy-v1', ?, '192.0.2.10', ?)
                """, consentId, userId, timestamp, timestamp);

        jdbcTemplate.update("""
                insert into budget_recommendations
                    (id, user_id, summary, recommendations_payload, potential_monthly_savings, created_at, expires_at)
                values (?, ?, 'Summary', '{"items":[]}'::jsonb, 10.00, ?, ?)
                """, recommendationId, userId, timestamp, Timestamp.from(now.plusSeconds(3600)));

        jdbcTemplate.update("""
                insert into audit_logs (user_id, action, resource_type, resource_id, details, ip_address, timestamp)
                values (?, 'CREATE_VAULT', 'SMART_VAULT', ?, '{"vaultName":"Personal savings"}'::jsonb, '192.0.2.10', ?)
                """, userId, vaultId.toString(), timestamp);

        if (withLedger) {
            UUID transientIntentVaultId = transientVaultId;
            jdbcTemplate.update("""
                    insert into smart_vaults
                        (id, user_id, vault_name, vault_type, current_balance, has_card_linked, created_at, updated_at)
                    values (?, ?, 'Temporary goal', 'GOAL', 0, false, ?, ?)
                    """, transientIntentVaultId, userId, timestamp, timestamp);
            jdbcTemplate.update("""
                    insert into vault_lock_intents
                        (id, user_id, vault_id, amount, sca_challenge, status, expires_at, created_at)
                    values (?, ?, ?, 9.99, 'transient-challenge', 'PENDING', ?, ?)
                    """, transientIntentId, userId, transientIntentVaultId, Timestamp.from(now.plusSeconds(60)), timestamp);
        }

        if (withLedger) {
            jdbcTemplate.update("""
                    insert into vault_lock_intents
                        (id, user_id, vault_id, amount, sca_challenge, status, expires_at, created_at)
                    values (?, ?, ?, 125.50, 'provider-challenge-secret', 'CONFIRMED', ?, ?)
                    """, intentId, userId, vaultId, Timestamp.from(now.plusSeconds(60)), timestamp);
            jdbcTemplate.update("""
                    insert into vault_ledger_entries
                        (id, user_id, vault_id, intent_id, idempotency_key, amount, entry_type, created_at)
                    values (?, ?, ?, ?, 'request-key', 125.50, 'LOCK_INFLOW', ?)
                    """, ledgerEntryId, userId, vaultId, intentId, timestamp);
        }
    }
}

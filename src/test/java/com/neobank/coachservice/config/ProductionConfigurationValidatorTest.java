package com.neobank.coachservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {

    private static final String DATABASE_URL = "jdbc:postgresql://db.internal:5432/coach";
    private static final String DATABASE_USER = "coach_service";
    private static final String DATABASE_PASSWORD = "strong-production-password";
    private static final String AI_KEY = "production-groq-secret";
    private static final String AI_BASE_URL = "https://api.groq.com/openai";
    private static final String JWKS_URI = "https://identity.example.com/.well-known/jwks.json";
    private static final String ISSUER = "https://identity.example.com";
    private static final String AUDIENCE = "financial-coach-service";
    private static final String TRANSACTION_URL = "https://transactions.example.com";
    private static final String SCA_URL = "https://sca.example.com";
    private static final String SCA_TOKEN = "production-sca-secret";

    @Test
    void acceptsExplicitProductionConfiguration() {
        assertThatCode(() -> newValidator(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD,
                AI_KEY,
                AI_BASE_URL,
                JWKS_URI,
                ISSUER,
                AUDIENCE,
                TRANSACTION_URL
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsDefaultDatabaseCredentials() {
        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, "postgres", DATABASE_PASSWORD, AI_KEY, AI_BASE_URL,
                JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.username");

        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, DATABASE_USER, "password", AI_KEY, AI_BASE_URL,
                JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password");
    }

    @Test
    void rejectsLocalDatabaseAndMockAiKey() {
        assertThatThrownBy(() -> newValidator(
                "jdbc:postgresql://localhost:5432/coach", DATABASE_USER, DATABASE_PASSWORD,
                AI_KEY, AI_BASE_URL, JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("localhost");

        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, "mock-key-for-dev", AI_BASE_URL,
                JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }

    @Test
    void requiresHttpsForIdentityAndInternalServiceUrls() {
        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, AI_KEY,
                "http://api.groq.com/openai", JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_BASE_URL");

        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, AI_KEY, AI_BASE_URL,
                "http://identity.example.com/jwks", ISSUER, AUDIENCE, TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWKS_URI");

        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, AI_KEY, AI_BASE_URL,
                JWKS_URI, ISSUER, AUDIENCE, "http://transactions.example.com"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICES_TRANSACTION_SERVICE_BASE_URL");
    }

    @Test
    void rejectsMissingOrPlaceholderProductionValues() {
        assertThatThrownBy(() -> newValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, AI_KEY, AI_BASE_URL,
                JWKS_URI, ISSUER, "change-me-audience", TRANSACTION_URL
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_AUDIENCE");
    }

    @Test
    void requiresSecureConfiguredScaProvider() {
        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, AI_KEY, AI_BASE_URL,
                JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL, "http://sca.example.com", SCA_TOKEN
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCA_PROVIDER_BASE_URL");

        assertThatThrownBy(() -> new ProductionConfigurationValidator(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, AI_KEY, AI_BASE_URL,
                JWKS_URI, ISSUER, AUDIENCE, TRANSACTION_URL, SCA_URL, ""
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCA_PROVIDER_API_TOKEN");
    }

    private ProductionConfigurationValidator newValidator(
            String databaseUrl,
            String databaseUser,
            String databasePassword,
            String aiKey,
            String aiBaseUrl,
            String jwksUri,
            String issuer,
            String audience,
            String transactionUrl) {
        return new ProductionConfigurationValidator(
                databaseUrl, databaseUser, databasePassword, aiKey, aiBaseUrl,
                jwksUri, issuer, audience, transactionUrl, SCA_URL, SCA_TOKEN);
    }
}

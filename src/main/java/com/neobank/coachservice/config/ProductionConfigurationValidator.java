package com.neobank.coachservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
@Profile("prod")
public class ProductionConfigurationValidator {

    public ProductionConfigurationValidator(
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${spring.ai.openai.api-key}") String aiApiKey,
            @Value("${spring.ai.openai.base-url}") String aiBaseUrl,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwksUri,
            @Value("${security.jwt.issuer}") String jwtIssuer,
            @Value("${security.jwt.audience}") String jwtAudience,
            @Value("${services.transaction-service.base-url}") String transactionServiceBaseUrl,
            @Value("${services.sca.base-url}") String scaProviderBaseUrl,
            @Value("${services.sca.api-token}") String scaProviderApiToken) {
        requirePostgresUrl(datasourceUrl);
        rejectLocalHost(datasourceUrl, "spring.datasource.url");
        rejectValue(datasourceUsername, "postgres", "spring.datasource.username");
        rejectPlaceholder(datasourcePassword, "spring.datasource.password");
        rejectPlaceholder(aiApiKey, "GROQ_API_KEY");
        requireHttps(aiBaseUrl, "AI_BASE_URL");
        requireHttps(jwksUri, "JWKS_URI");
        requireHttps(jwtIssuer, "JWT_ISSUER");
        rejectPlaceholder(jwtAudience, "JWT_AUDIENCE");
        requireHttps(transactionServiceBaseUrl, "SERVICES_TRANSACTION_SERVICE_BASE_URL");
        requireHttps(scaProviderBaseUrl, "SCA_PROVIDER_BASE_URL");
        rejectPlaceholder(scaProviderApiToken, "SCA_PROVIDER_API_TOKEN");
    }

    private static void requirePostgresUrl(String value) {
        if (value == null || !value.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Production requires a PostgreSQL SPRING_DATASOURCE_URL.");
        }
    }

    private static void rejectLocalHost(String jdbcUrl, String propertyName) {
        String lowerCaseUrl = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lowerCaseUrl.matches("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1|\\[::1\\])([:/?].*)?$")) {
            throw new IllegalStateException(propertyName + " must not point to localhost in production.");
        }
    }

    private static void rejectValue(String value, String rejectedValue, String propertyName) {
        if (value == null || value.isBlank() || rejectedValue.equalsIgnoreCase(value.trim())) {
            throw new IllegalStateException(propertyName + " must be explicitly configured for production.");
        }
    }

    private static void rejectPlaceholder(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be explicitly configured for production.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("change-me")
                || normalized.contains("replace-me")
                || normalized.contains("mock-key-for-dev")
                || "password".equals(normalized)
                || "postgres".equals(normalized)) {
            throw new IllegalStateException(propertyName + " contains a development placeholder.");
        }
    }

    private static void requireHttps(String value, String propertyName) {
        rejectPlaceholder(value, propertyName);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(propertyName + " must be a valid HTTPS URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException(propertyName + " must be a valid HTTPS URL in production.");
        }
    }
}

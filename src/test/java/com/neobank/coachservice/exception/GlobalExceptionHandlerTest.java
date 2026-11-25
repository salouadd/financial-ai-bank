package com.neobank.coachservice.exception;

import com.neobank.coachservice.monitoring.SecurityMonitoringMetrics;
import com.neobank.coachservice.sca.ScaProviderUnavailableException;
import com.neobank.coachservice.exception.RequestRateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new SecurityMonitoringMetrics(registry));

    @Test
    void resourceNotFoundUsesSafeStandardProblemResponse() {
        var problem = handler.handleNotFound(new EntityNotFoundException("internal database detail"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).doesNotContain("database");
        assertThat(problem.getProperties()).containsEntry("code", "resource-not-found");
    }

    @Test
    void providerFailuresAreSanitizedAndCountedWithFixedTags() {
        var scaProblem = handler.handleScaProviderUnavailable(
                new ScaProviderUnavailableException(new IllegalStateException("token-value")));
        var transactionProblem = handler.handleExternalProviderUnavailable(
                new ResourceAccessException("https://internal.example/path?token=secret"));

        assertThat(scaProblem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(scaProblem.getDetail()).doesNotContain("token-value");
        assertThat(transactionProblem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(transactionProblem.getDetail()).doesNotContain("secret");
        assertThat(registry.get("coach.provider.failures").tag("provider", "sca").counter().count()).isEqualTo(1);
        assertThat(registry.get("coach.provider.failures")
                .tag("provider", "transaction_service").counter().count()).isEqualTo(1);
        assertThat(registry.get("coach.security.sca.events")
                .tag("outcome", "provider_unavailable").counter()                .count()).isEqualTo(1);
    }

    @Test
    void rateLimitErrorsExposeSafeDetailsAndIncrementBoundedMetrics() {
        var problem = handler.handleRateLimitExceeded(new RequestRateLimitExceededException("ai-analysis"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(problem.getDetail()).doesNotContain("ai-analysis");
        assertThat(registry.get("coach.security.rate_limit.events")
                .tag("operation", "ai_analysis").counter().count()).isEqualTo(1);
    }
}

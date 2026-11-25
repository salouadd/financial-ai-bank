package com.neobank.coachservice.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SecurityMonitoringMetrics {

    private final MeterRegistry meterRegistry;

    public SecurityMonitoringMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void authenticationFailure(String outcome) {
        meterRegistry.counter("coach.security.authentication.failures", "outcome", outcome).increment();
    }

    public void scaEvent(String outcome) {
        meterRegistry.counter("coach.security.sca.events", "outcome", outcome).increment();
    }

    public void providerFailure(String provider) {
        meterRegistry.counter("coach.provider.failures", "provider", provider).increment();
    }

    public void aiAnalysis(String outcome) {
        meterRegistry.counter("coach.ai.analysis.events", "outcome", outcome).increment();
    }

    public void rateLimitExceeded(String operation) {
        meterRegistry.counter("coach.security.rate_limit.events", "operation", operation).increment();
    }
}

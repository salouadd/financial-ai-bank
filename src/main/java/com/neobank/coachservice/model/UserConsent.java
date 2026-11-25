package com.neobank.coachservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_consents")
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "ai_processing_consented", nullable = false)
    private boolean aiProcessingConsented;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;

    @Column(name = "consented_at")
    private Instant consentedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserConsent() {}

    public UserConsent(UUID userId, boolean consented, String policyVersion, String ipAddress) {
        this.userId = userId;
        this.aiProcessingConsented = consented;
        this.policyVersion = policyVersion;
        this.ipAddress = ipAddress;
        if (consented) {
            this.consentedAt = Instant.now();
        } else {
            this.revokedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void grantConsent(String version, String ipAddress) {
        this.aiProcessingConsented = true;
        this.policyVersion = version;
        this.ipAddress = ipAddress;
        this.consentedAt = Instant.now();
        this.revokedAt = null;
        this.updatedAt = Instant.now();
    }

    public void revokeConsent(String ipAddress) {
        this.aiProcessingConsented = false;
        this.ipAddress = ipAddress;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public boolean isAiProcessingConsented() { return aiProcessingConsented; }
    public String getPolicyVersion() { return policyVersion; }
    public Instant getConsentedAt() { return consentedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getIpAddress() { return ipAddress; }
    public Instant getUpdatedAt() { return updatedAt; }
}
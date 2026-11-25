package com.neobank.coachservice.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class JwtPrincipalUtils {

    private JwtPrincipalUtils() {
    }

    public static UUID userId(Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId != null && !userId.isBlank()) {
            return UUID.fromString(userId);
        }

        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return UUID.fromString(subject);
        }

        throw new IllegalArgumentException("JWT does not contain a valid user identifier");
    }
}

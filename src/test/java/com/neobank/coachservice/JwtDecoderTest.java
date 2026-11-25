package com.neobank.coachservice;

import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderTest {

    private static final String ISSUER = "https://identity.example.test";
    private static final String AUDIENCE = "financial-coach-service";

    private final SecurityConfig securityConfig = new SecurityConfig();
    private RSAKey trustedSigningKey;
    private HttpServer jwksServer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        trustedSigningKey = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
        jwksServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] jwks = new JWKSet(trustedSigningKey.toPublicJWK()).toString().getBytes();
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        jwksServer.start();

        String jwksUri = "http://localhost:" + jwksServer.getAddress().getPort() + "/jwks";
        decoder = securityConfig.jwtDecoder(jwksUri, ISSUER, AUDIENCE);
    }

    @AfterEach
    void tearDown() {
        jwksServer.stop(0);
    }

    @Test
    void acceptsTokenWithTrustedSignatureIssuerAudienceAndLifetime() throws Exception {
        String token = createToken(trustedSigningKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));

        assertThat(decoder.decode(token).getSubject()).isEqualTo("test-user");
    }

    @Test
    void rejectsTokenFromUnexpectedIssuer() throws Exception {
        String token = createToken(
                trustedSigningKey, "https://untrusted.example.test", AUDIENCE, Instant.now().plusSeconds(60)
        );

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenForUnexpectedAudience() throws Exception {
        String token = createToken(
                trustedSigningKey, ISSUER, "another-api", Instant.now().plusSeconds(60)
        );

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = createToken(
                trustedSigningKey, ISSUER, AUDIENCE, Instant.now().minusSeconds(60)
        );

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedByUntrustedKey() throws Exception {
        RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
        String token = createToken(untrustedKey, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String createToken(RSAKey signingKey, String issuer, String audience, Instant expiresAt)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("test-user")
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .claim("role", "USER")
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims
        );
        signedJwt.sign(new RSASSASigner(signingKey));
        return signedJwt.serialize();
    }
}

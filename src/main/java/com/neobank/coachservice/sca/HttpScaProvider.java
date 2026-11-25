package com.neobank.coachservice.sca;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

public class HttpScaProvider implements ScaProvider {

    private final RestClient restClient;

    public HttpScaProvider(
            RestClient.Builder restClientBuilder,
            org.springframework.core.env.Environment environment) {
        String baseUrl = environment.getRequiredProperty("services.sca.base-url");
        String apiToken = environment.getRequiredProperty("services.sca.api-token");
        if (apiToken.isBlank()) {
            throw new IllegalStateException("SCA_PROVIDER_API_TOKEN must be configured when the SCA provider is enabled.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String issueChallenge(ChallengeRequest request) {
        try {
            IssueChallengeResponse response = restClient.post()
                    .uri("/v1/challenges")
                    .body(request)
                    .retrieve()
                    .body(IssueChallengeResponse.class);
            if (response == null || response.challengeId() == null
                    || !response.challengeId().matches("[A-Za-z0-9._~-]{1,256}")) {
                throw new ScaProviderUnavailableException();
            }
            return response.challengeId();
        } catch (RestClientException ex) {
            throw new ScaProviderUnavailableException(ex);
        }
    }

    @Override
    public boolean verifyChallenge(VerificationRequest request) {
        try {
            VerificationResponse response = restClient.post()
                    .uri("/v1/challenges/{challengeId}/verify", request.challengeId())
                    .body(new VerifyChallengeRequest(
                            request.userId(),
                            request.intentId(),
                            request.vaultId(),
                            request.amount(),
                            request.code()))
                    .retrieve()
                    .body(VerificationResponse.class);
            if (response == null || response.verified() == null) {
                throw new ScaProviderUnavailableException();
            }
            return response.verified();
        } catch (RestClientException ex) {
            throw new ScaProviderUnavailableException(ex);
        }
    }

    private record IssueChallengeResponse(String challengeId) {
    }

    private record VerifyChallengeRequest(
            java.util.UUID userId,
            java.util.UUID intentId,
            java.util.UUID vaultId,
            java.math.BigDecimal amount,
            String code) {
    }

    private record VerificationResponse(Boolean verified) {
    }
}

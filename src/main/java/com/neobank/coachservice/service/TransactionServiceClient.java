package com.neobank.coachservice.client;

import com.neobank.coachservice.dto.SalaryDetectionDTOs.TransactionFeedItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class TransactionServiceClient {

    private final RestClient restClient;

    public TransactionServiceClient(
            @Value("${services.transaction-service.base-url:https://transaction-service.internal:8443}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "financial-coach-service")
                .build();
    }

    /**
     * Récupère les 90 derniers jours de transactions pour analyse de récurrence
     */
    public List<TransactionFeedItem> fetchRecentInflows(UUID userId, String internalServiceJwt) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/users/{userId}/inflows")
                        .queryParam("since", LocalDate.now().minusDays(90).toString())
                        .build(userId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalServiceJwt)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TransactionFeedItem>>() {});
    }
}
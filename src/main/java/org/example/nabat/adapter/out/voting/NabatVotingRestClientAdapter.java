package org.example.nabat.adapter.out.voting;

import org.example.nabat.application.port.out.ExternalVotingPort;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class NabatVotingRestClientAdapter implements ExternalVotingPort {

    private final RestClient restClient;
    private final String authToken;

    public NabatVotingRestClientAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${nabat.voting.service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${nabat.voting.service.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${nabat.voting.service.read-timeout:PT3S}") Duration readTimeout,
            @Value("${nabat.voting.service.auth-token:}") String authToken
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.authToken = authToken;
    }

    @Override
    public VoteReceipt vote(AlertId alertId, UserId userId, VoteType voteType) {
        try {
            VoteResponse response = withAuthHeaders(
                    restClient.post()
                            .uri("/api/v1/alerts/{alertId}/votes", alertId.value())
                            .body(new VoteRequest(userId.value(), voteType))
            ).retrieve().body(VoteResponse.class);

            if (response == null) {
                throw new IllegalStateException("Voting service returned an empty vote response");
            }

            return response.toDomain();
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Voting service is unavailable", ex);
        }
    }

    @Override
    public void removeVote(AlertId alertId, UserId userId) {
        try {
            withAuthHeaders(
                    restClient.delete().uri(uriBuilder -> uriBuilder
                            .path("/api/v1/alerts/{alertId}/votes")
                            .queryParam("userId", userId.value())
                            .build(alertId.value()))
            ).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Voting service is unavailable", ex);
        }
    }

    @Override
    public boolean hasUserVoted(AlertId alertId, UserId userId) {
        try {
            UserVoteResponse response = withAuthHeaders(
                    restClient.get().uri(uriBuilder -> uriBuilder
                            .path("/api/v1/alerts/{alertId}/votes/me")
                            .queryParam("userId", userId.value())
                            .build(alertId.value()))
            ).retrieve().body(UserVoteResponse.class);

            return response != null && response.hasVoted();
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Voting service is unavailable", ex);
        }
    }

    @Override
    public VoteStats getVoteStats(AlertId alertId) {
        try {
            VoteStatsResponse response = withAuthHeaders(
                    restClient.get().uri("/api/v1/alerts/{alertId}/votes/stats", alertId.value())
            ).retrieve().body(VoteStatsResponse.class);

            if (response == null) {
                return new VoteStats(0, 0, 0, 0);
            }
            return new VoteStats(
                    response.upvotes(),
                    response.downvotes(),
                    response.confirmations(),
                    response.credibilityScore()
            );
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Voting service is unavailable", ex);
        }
    }

    private RestClient.RequestHeadersSpec<?> withAuthHeaders(RestClient.RequestHeadersSpec<?> requestSpec) {
        if (!StringUtils.hasText(authToken)) {
            return requestSpec;
        }
        return requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
    }

    private RuntimeException mapHttpException(RestClientResponseException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        if (statusCode.value() == 409) {
            return new IllegalStateException("Voting request conflicts with current state");
        }
        if (statusCode.value() == 404) {
            return new IllegalArgumentException("Alert not found in voting service");
        }
        return new IllegalStateException("Voting service request failed with status " + statusCode.value());
    }

    private record VoteRequest(
            UUID userId,
            VoteType voteType
    ) {
    }

    private record VoteResponse(
            UUID id,
            UUID alertId,
            VoteType voteType,
            String createdAt
    ) {
        VoteReceipt toDomain() {
            return new VoteReceipt(
                    id,
                    AlertId.of(alertId),
                    voteType,
                    java.time.Instant.parse(createdAt)
            );
        }
    }

    private record VoteStatsResponse(
            int upvotes,
            int downvotes,
            int confirmations,
            int credibilityScore
    ) {
    }

    private record UserVoteResponse(boolean hasVoted) {
    }
}

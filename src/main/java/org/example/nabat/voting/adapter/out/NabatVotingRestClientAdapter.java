package org.example.nabat.voting.adapter.out;

import org.example.nabat.shared.exception.ExternalServiceUnavailableException;
import org.example.nabat.voting.application.port.out.ExternalVotingPort;
import org.example.nabat.identity.application.port.out.RequestContextPort;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.voting.domain.VoteConflictException;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for the nabat-voting service.
 *
 * <h2>Caller identity</h2>
 * The voting service derives the voter from the {@code userId} claim of the bearer
 * token it receives. This adapter therefore forwards <em>the end user's</em> access
 * token from the current request.
 *
 * <p>It previously sent a single shared {@code nabat.voting.service.auth-token} and
 * put the acting user's id in the request body. The voting service ignored that
 * body field, so every vote was attributed to whatever identity the shared token
 * carried — collapsing all users onto one voter and tripping the
 * {@code (alert_id, voter_id)} unique constraint for the second user to vote on any
 * alert. And because the token defaulted to empty, the usual outcome was a 401
 * that surfaced to the browser as a 409.
 *
 * <p>The configured service token is retained only as a fallback for calls made
 * outside a user request (there are none today; it exists so a future scheduled
 * reconciliation job has a supported path).
 */
@Component
public class NabatVotingRestClientAdapter implements ExternalVotingPort {

    private static final Logger log = LoggerFactory.getLogger(NabatVotingRestClientAdapter.class);

    private final RestClient restClient;
    private final RequestContextPort requestContext;
    private final String fallbackServiceToken;

    public NabatVotingRestClientAdapter(
            RestClient.Builder restClientBuilder,
            RequestContextPort requestContext,
            @Value("${nabat.voting.service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${nabat.voting.service.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${nabat.voting.service.read-timeout:PT3S}") Duration readTimeout,
            @Value("${nabat.voting.service.auth-token:}") String fallbackServiceToken
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
        this.requestContext = requestContext;
        this.fallbackServiceToken = fallbackServiceToken;
    }

    @Override
    public VoteResult vote(AlertId alertId, UserId userId, VoteType voteType) {
        VoteResponse response = exchange(
                "cast vote",
                () -> authorized(restClient.post()
                        .uri("/api/v1/alerts/{alertId}/votes", alertId.value())
                        // No userId in the body: the voting service takes the voter from the
                        // forwarded token, and rejects a body userId that disagrees with it.
                        .body(new VoteRequest(voteType)))
                        .retrieve()
                        .body(VoteResponse.class));

        if (response == null) {
            throw new ExternalServiceUnavailableException(
                    "Voting service returned an empty response to a vote");
        }
        return response.toDomain(alertId);
    }

    @Override
    public VoteStats removeVote(AlertId alertId, UserId userId) {
        VoteStatsResponse response = exchange(
                "remove vote",
                () -> authorized(restClient.delete()
                        .uri("/api/v1/alerts/{alertId}/votes", alertId.value()))
                        .retrieve()
                        .body(VoteStatsResponse.class));

        return response == null ? VoteStats.EMPTY : response.toDomain();
    }

    @Override
    public Optional<VoteType> findUserVote(AlertId alertId, UserId userId) {
        UserVoteResponse response = exchange(
                "read own vote",
                () -> authorized(restClient.get()
                        .uri("/api/v1/alerts/{alertId}/votes/me", alertId.value()))
                        .retrieve()
                        .body(UserVoteResponse.class));

        return Optional.ofNullable(response).map(UserVoteResponse::voteType);
    }

    @Override
    public VoteStats getVoteStats(AlertId alertId) {
        VoteStatsResponse response = exchange(
                "read vote stats",
                () -> authorized(restClient.get()
                        .uri("/api/v1/alerts/{alertId}/votes/stats", alertId.value()))
                        .retrieve()
                        .body(VoteStatsResponse.class));

        return response == null ? VoteStats.EMPTY : response.toDomain();
    }

    /** Runs a call, translating transport and status failures into meaningful exceptions. */
    private <T> T exchange(String operation, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            throw translate(operation, ex);
        } catch (RestClientException ex) {
            // Connection refused, DNS failure, read timeout…
            throw new ExternalServiceUnavailableException(
                    "Voting service is unreachable (" + operation + ")", ex);
        }
    }

    private RestClient.RequestHeadersSpec<?> authorized(RestClient.RequestHeadersSpec<?> spec) {
        String token = requestContext.callerAccessToken()
                .orElse(StringUtils.hasText(fallbackServiceToken) ? fallbackServiceToken : null);

        if (token == null) {
            throw new ExternalServiceUnavailableException(
                    "No access token available to authenticate against the voting service. "
                            + "Vote requests must carry the caller's bearer token, or "
                            + "nabat.voting.service.auth-token must be configured for non-request callers.");
        }
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /**
     * Maps voting-service status codes onto our own semantics.
     *
     * <p>Only 404 and 409 say anything about the user's request. 401/403 mean our
     * credential propagation is broken, and 5xx means the service is unhealthy —
     * both are our problem, so they become 503 rather than being reported to the
     * user as a conflict.
     */
    private RuntimeException translate(String operation, RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();

        if (status.value() == HttpStatus.CONFLICT.value()) {
            return new VoteConflictException("You have already cast this vote on the alert");
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return new AlertNotFoundException("Alert not found in the voting service");
        }
        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                || status.value() == HttpStatus.FORBIDDEN.value()) {
            log.error("Voting service rejected our credentials ({}) on '{}' — the forwarded access "
                    + "token was not accepted. Check that both services share the same JWT_SECRET.",
                    status.value(), operation);
            return new ExternalServiceUnavailableException("Voting service authentication failed");
        }

        log.error("Voting service returned {} on '{}'", status.value(), operation);
        return new ExternalServiceUnavailableException(
                "Voting service request failed with status " + status.value());
    }

    private record VoteRequest(VoteType voteType) {
    }

    private record VoteResponse(
            UUID id,
            UUID alertId,
            VoteType voteType,
            // Typed as Instant so Jackson validates the format at the boundary instead of
            // deferring an unhandled DateTimeParseException to the mapping code.
            Instant createdAt,
            VoteStatsResponse stats
    ) {
        VoteResult toDomain(AlertId requestedAlertId) {
            return new VoteResult(
                    id,
                    alertId == null ? requestedAlertId : AlertId.of(alertId),
                    voteType,
                    createdAt == null ? Instant.now() : createdAt,
                    stats == null ? VoteStats.EMPTY : stats.toDomain()
            );
        }
    }

    private record VoteStatsResponse(
            int upvotes,
            int downvotes,
            int confirmations,
            int credibilityScore
    ) {
        VoteStats toDomain() {
            return new VoteStats(upvotes, downvotes, confirmations, credibilityScore);
        }
    }

    private record UserVoteResponse(boolean hasVoted, VoteType voteType) {
    }
}

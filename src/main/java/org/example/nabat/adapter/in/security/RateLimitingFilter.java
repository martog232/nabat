package org.example.nabat.adapter.in.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Servlet filter that enforces a per-IP token-bucket rate limit on
 * {@code POST /api/v1/auth/login} and {@code POST /api/v1/auth/register}.
 *
 * <p>When the bucket for a given IP is exhausted the request is rejected with
 * {@code HTTP 429 Too Many Requests} and a
 * {@code X-RateLimit-Retry-After-Seconds} response header.
 *
 * <p>Configuration (overridable via environment variables):
 * <ul>
 *   <li>{@code nabat.rate-limit.auth.capacity}  — max requests per window (default 5)</li>
 *   <li>{@code nabat.rate-limit.auth.window-minutes} — refill window in minutes (default 15)</li>
 * </ul>
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final String LOGIN_PATH    = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String TOO_MANY      = "Too many requests. Please try again later.";

    private final int capacity;
    private final Duration window;

    /** Per-IP bucket store. Production deployments with multiple instances should
     *  replace this with a Redis-backed Bucket4j ProxyManager. */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${nabat.rate-limit.auth.capacity:5}") int capacity,
            @Value("${nabat.rate-limit.auth.window-minutes:15}") int windowMinutes
    ) {
        this.capacity = capacity;
        this.window   = Duration.ofMinutes(windowMinutes);
    }

    // ── OncePerRequestFilter ─────────────────────────────────────────────────

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path   = request.getRequestURI();
        boolean isAuthPost = "POST".equalsIgnoreCase(method)
                && (LOGIN_PATH.equals(path) || REGISTER_PATH.equals(path));
        return !isAuthPost;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds =
                    TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            log.warn("Rate limit exceeded for IP {} on {}", ip, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_OK); // set 429 below
            response.setStatus(429);
            response.addHeader("X-RateLimit-Retry-After-Seconds",
                    String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"" + TOO_MANY + "\"}");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, window)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extracts the originating client IP, honouring common reverse-proxy
     * headers ({@code X-Forwarded-For}, {@code X-Real-IP}).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()
                && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()
                && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Exposed for testing — allows resetting the per-IP bucket store between
     * test cases without restarting the application context.
     */
    public void resetBuckets() {
        buckets.clear();
    }
}


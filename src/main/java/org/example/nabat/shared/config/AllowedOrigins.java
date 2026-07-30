package org.example.nabat.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single source of truth for the browser origins allowed to reach this service,
 * over both HTTP (CORS) and the WebSocket handshake.
 *
 * <p>Previously {@code SecurityConfig} and {@code WebSocketConfig} each parsed
 * {@code nabat.cors.allowed-origins} and each carried its own copy of the
 * localhost dev fallback — so the two could drift, and only one of them stripped
 * blank entries.
 */
@Component
public class AllowedOrigins {

    /**
     * Dev fallback used when nothing is configured, so a Vite (5173) or CRA (3000)
     * dev server works out of the box without opening the service to public origins.
     */
    private static final List<String> LOCALHOST_DEV_ORIGINS = List.of(
        "http://localhost:5173", "http://127.0.0.1:5173",
        "http://localhost:3000", "http://127.0.0.1:3000"
    );

    private final List<String> patterns;

    public AllowedOrigins(@Value("${nabat.cors.allowed-origins:}") List<String> configured) {
        List<String> cleaned = configured.stream()
            .map(String::trim)
            // An empty property binds to [""], which would otherwise masquerade as a
            // configured origin and match nothing.
            .filter(s -> !s.isEmpty())
            .toList();

        // Credentialed requests are enabled, so a bare "*" would let any site on the
        // internet read authenticated responses. Spring permits this combination with
        // allowedOriginPatterns (unlike allowedOrigins), so it has to be refused here.
        if (cleaned.contains("*")) {
            throw new IllegalStateException(
                "nabat.cors.allowed-origins must not be '*': credentials are enabled, so a "
                    + "wildcard would expose authenticated responses to any origin. "
                    + "List the exact origins instead."
            );
        }

        this.patterns = cleaned.isEmpty() ? LOCALHOST_DEV_ORIGINS : cleaned;
    }

    /** Effective origin patterns — never empty. */
    public List<String> patterns() {
        return patterns;
    }

    public String[] toArray() {
        return patterns.toArray(String[]::new);
    }
}

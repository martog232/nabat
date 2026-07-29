package org.example.nabat.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.nabat.application.port.out.TokenProvider;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(TokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt)) {
                authenticate(jwt, request);
            }
        } catch (Exception ex) {
            // Clear any partial authentication state to prevent security leaks.
            SecurityContextHolder.clearContext();
            // Debug, not error-with-stack-trace: this branch is reachable with any
            // malformed bearer token, so logging loudly here let an unauthenticated
            // caller flood the logs.
            logger.debug("Authentication failed", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Verifies the token once and, if it names a live user whose session has not been
     * invalidated, populates the security context.
     *
     * <p>The signature used to be verified three times per request — once each by
     * {@code validateToken}, {@code isAccessToken} and {@code getUserIdFromToken}.
     */
    private void authenticate(String jwt, HttpServletRequest request) {
        Optional<TokenProvider.AccessTokenClaims> parsed = tokenProvider.parseAccessToken(jwt);
        if (parsed.isEmpty()) {
            reject("invalid or non-access token");
            return;
        }
        TokenProvider.AccessTokenClaims claims = parsed.get();

        User user = userRepository.findById(UserId.of(claims.userId())).orElse(null);
        if (user == null) {
            reject("token names a user that no longer exists");
            return;
        }
        if (!user.enabled()) {
            reject("user is disabled");
            return;
        }
        // A password reset or an explicit revocation bumps tokenVersion, which makes
        // every token minted before that point stale even though it is still within
        // its expiry window.
        if (claims.tokenVersion() != user.tokenVersion()) {
            reject("token was invalidated by a credential change");
            return;
        }

        List<SimpleGrantedAuthority> authorities =
            List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Refuses the presented token and clears the security context.
     *
     * <p>Clearing matters: a request that carries a bearer token must never end up
     * authenticated as whatever happened to be in the context already. The chain is
     * stateless so the context is normally empty, but this makes the guarantee
     * independent of that.
     */
    private void reject(String reason) {
        SecurityContextHolder.clearContext();
        logger.debug("Rejected request: " + reason);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

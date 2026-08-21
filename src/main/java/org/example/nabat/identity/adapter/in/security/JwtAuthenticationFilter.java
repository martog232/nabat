package org.example.nabat.identity.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.nabat.identity.application.port.in.AuthenticateSessionUseCase;
import org.example.nabat.identity.domain.User;
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

    private final AuthenticateSessionUseCase authenticateSessionUseCase;

    public JwtAuthenticationFilter(AuthenticateSessionUseCase authenticateSessionUseCase) {
        this.authenticateSessionUseCase = authenticateSessionUseCase;
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
     * Populates the security context if the token still names an accepted session.
     *
     * <p>Whether it does is decided by {@link AuthenticateSessionUseCase}, not here:
     * the WebSocket handshake asks the same question, and while these checks lived in
     * this filter it answered it with a signature check alone.
     */
    private void authenticate(String jwt, HttpServletRequest request) {
        Optional<User> authenticated = authenticateSessionUseCase.authenticateAccessToken(jwt);
        if (authenticated.isEmpty()) {
            // A request carrying a bearer token must never end up authenticated as
            // whatever happened to be in the context already. The chain is stateless
            // so it is normally empty; this makes the guarantee independent of that.
            SecurityContextHolder.clearContext();
            return;
        }
        User user = authenticated.get();

        List<SimpleGrantedAuthority> authorities =
            List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

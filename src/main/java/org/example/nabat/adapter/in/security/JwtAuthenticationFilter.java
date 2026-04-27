package org.example.nabat.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
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
                // 1. Validate token structure and signature
                if (!jwtTokenProvider.validateToken(jwt)) {
                    logger.debug("Invalid JWT token signature or structure");
                    filterChain.doFilter(request, response);
                    return;
                }

                // 2. Reject refresh tokens — only access tokens allowed here
                if (!jwtTokenProvider.isAccessToken(jwt)) {
                    logger.warn("Attempted to use non-access token (likely refresh token) for authentication");
                    filterChain.doFilter(request, response);
                    return;
                }

                // 3. Extract userId (more stable than email, which can change)
                String userIdStr = jwtTokenProvider.getUserIdFromToken(jwt);
                UUID userId = UUID.fromString(userIdStr);

                // 4. Load user from repository
                User user = userRepository.findById(UserId.of(userId)).orElse(null);

                // 5. Authenticate only if user exists and is enabled
                if (user != null && user.enabled()) {
                    // Build authorities from user role (extensible to permissions later)
                    List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.role().name())
                    );

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (user != null && !user.enabled()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("User " + userId + " is disabled, authentication skipped");
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("User " + userId + " not found in repository");
                    }
                }
            }
        } catch (Exception ex) {
            // Clear any partial authentication state to prevent security leaks
            SecurityContextHolder.clearContext();
            logger.error("Authentication failed due to exception: " + ex.getMessage(), ex);
            // Continue filter chain as unauthenticated — controller will return 401 if endpoint requires auth
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

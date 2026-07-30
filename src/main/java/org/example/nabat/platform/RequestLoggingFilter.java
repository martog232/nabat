package org.example.nabat.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.nabat.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs incoming requests and outgoing responses when {@code logging.nabat.request-log=true}.
 *
 * <p>Log format: {@code [METHOD] /path  →  STATUS  (Nms)  [userId=...]}</p>
 *
 * <p><strong>Enabled by default</strong> — {@code application.properties} sets
 * {@code logging.nabat.request-log=${NABAT_REQUEST_LOG:true}}. Set
 * {@code NABAT_REQUEST_LOG=false} to turn it off. (The javadoc here previously
 * claimed the opposite.)
 *
 * <h2>Ordering</h2>
 * Deliberately ordered <em>after</em> Spring Security's filter chain (which sits at
 * -100) rather than at {@code HIGHEST_PRECEDENCE}. Security's
 * {@code SecurityContextHolderFilter} clears the context in its own {@code finally}
 * block, so a filter that wraps the security chain runs its {@code finally} after
 * the context is already gone — which meant the {@code userId} field was empty on
 * every request, including authenticated ones.
 */
@Component
@Order(RequestLoggingFilter.ORDER)
@ConditionalOnProperty(name = "logging.nabat.request-log", havingValue = "true")
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** Just after Spring Security's chain, so the security context is still populated. */
    static final int ORDER = -90;

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String userId = resolveUserId();
            log.info("[{}] {}  →  {}  ({}ms){}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    userId.isEmpty() ? "" : "  [userId=" + userId + "]");
        }
    }

    private String resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
                return user.id().value().toString();
            }
        } catch (Exception ignored) {
            // Never let logging break the request
        }
        return "";
    }
}




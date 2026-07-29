package org.example.nabat.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import org.example.nabat.application.port.out.RequestContextPort;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Reads per-request facts off the current servlet request.
 *
 * <p>Replaces the former static {@code RequestContextHelper}, which the
 * application layer called directly.
 */
@Component
public class ServletRequestContextAdapter implements RequestContextPort {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public String clientIp() {
        return currentRequest()
            .map(ServletRequestContextAdapter::resolveClientIp)
            .orElse(UNKNOWN_IP);
    }

    @Override
    public Optional<String> callerAccessToken() {
        return currentRequest()
            .map(request -> request.getHeader("Authorization"))
            .filter(header -> header.startsWith(BEARER_PREFIX))
            .map(header -> header.substring(BEARER_PREFIX.length()))
            .filter(token -> !token.isBlank());
    }

    private static Optional<HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
            ? Optional.of(attrs.getRequest())
            : Optional.empty();
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = firstNonBlankHeader(request, "X-Forwarded-For");
        if (forwardedFor != null) {
            // Left-most entry is the original client; the rest are proxies.
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = firstNonBlankHeader(request, "X-Real-IP");
        if (realIp != null) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? UNKNOWN_IP : remoteAddr;
    }

    private static String firstNonBlankHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}

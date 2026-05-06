package org.example.nabat.config;

import org.example.nabat.adapter.in.websocket.AlertWebSocketHandler;
import org.example.nabat.adapter.in.websocket.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;

import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
        AlertWebSocketHandler alertWebSocketHandler,
        JwtHandshakeInterceptor jwtHandshakeInterceptor,
        @Value("${nabat.cors.allowed-origins:}") List<String> allowedOrigins
    ) {
        this.alertWebSocketHandler = alertWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        // Strip blanks so [""] from an empty property does not masquerade as a configured origin.
        this.allowedOrigins = allowedOrigins.stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketHandlerRegistration registration = registry
            .addHandler(alertWebSocketHandler, "/ws/alerts")
            .addInterceptors(jwtHandshakeInterceptor);

        // Same fallback as SecurityConfig: localhost dev servers when nothing is configured.
        List<String> effective = allowedOrigins.isEmpty()
            ? List.of(
                "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:3000", "http://127.0.0.1:3000"
            )
            : allowedOrigins;
        registration.setAllowedOriginPatterns(effective.toArray(String[]::new));
    }
}


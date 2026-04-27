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
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketHandlerRegistration registration = registry
            .addHandler(alertWebSocketHandler, "/ws/alerts")
            .addInterceptors(jwtHandshakeInterceptor);

        if (allowedOrigins.isEmpty()) {
            // No origins configured → only same-origin requests succeed.
            registration.setAllowedOrigins();
        } else {
            registration.setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
        }
    }
}


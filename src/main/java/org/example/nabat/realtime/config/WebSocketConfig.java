package org.example.nabat.realtime.config;

import org.example.nabat.shared.config.AllowedOrigins;
import org.example.nabat.realtime.adapter.in.websocket.AlertWebSocketHandler;
import org.example.nabat.realtime.adapter.in.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final AllowedOrigins allowedOrigins;

    public WebSocketConfig(
        AlertWebSocketHandler alertWebSocketHandler,
        JwtHandshakeInterceptor jwtHandshakeInterceptor,
        AllowedOrigins allowedOrigins
    ) {
        this.alertWebSocketHandler = alertWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(alertWebSocketHandler, "/ws/alerts")
            .addInterceptors(jwtHandshakeInterceptor)
            // Shared with SecurityConfig's CORS so the two cannot drift.
            .setAllowedOriginPatterns(allowedOrigins.toArray());
    }
}


package org.example.nabat.identity.config;

import org.springframework.http.HttpMethod;
import org.example.nabat.identity.adapter.in.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.example.nabat.shared.config.AllowedOrigins;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AllowedOrigins allowedOrigins;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        AllowedOrigins allowedOrigins
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection is disabled for stateless JWT authentication
            // JWT tokens in Authorization headers are not vulnerable to CSRF attacks
            // as they are not automatically sent by browsers like cookies
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Enumerated rather than /api/v1/auth/** — that wildcard also matched
                // GET /api/v1/auth/me, so the endpoint that returns the caller's own
                // profile was reachable unauthenticated. AuthController documents the
                // opposite and therefore omits a null check on the principal, so an
                // anonymous request reached UserResponse.from(null).
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/verify",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/ws/**").permitAll()
                // The probe groups are listed individually rather than as /actuator/health/**:
                // that wildcard would also expose any future health group, and `anyRequest()`
                // below is denyAll precisely so new endpoints are private until someone
                // decides otherwise. Kubernetes probes are unauthenticated, so these two
                // have to be public or every probe fails closed with 401 and the pod is
                // killed for a security rule rather than for being unhealthy.
                .requestMatchers("/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                // Fail closed. Previously `permitAll()`, which meant any endpoint added
                // outside /api/v1 — or any typo in a @RequestMapping — was silently public.
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Resolved once by AllowedOrigins, shared with the WebSocket handshake config.
        configuration.setAllowedOriginPatterns(allowedOrigins.patterns());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

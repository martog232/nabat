# Rate Limiting Implementation Guide for Nabat

## Step 1: Add Bucket4j Dependency

Add to `pom.xml`:

```xml
<!-- Rate Limiting -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>7.6.0</version>
</dependency>

<!-- Optional: Redis support for distributed rate limiting -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## Step 2: Create Rate Limiting Configuration

**File**: `src/main/java/org/example/nabat/adapter/in/ratelimiting/RateLimitingConfig.java`

```java
package org.example.nabat.adapter.in.ratelimiting;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitingConfig {
    
    /**
     * 5 attempts per 15 minutes per IP (for login/register endpoints)
     */
    public static Bucket createAuthLimitBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15)));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }
    
    /**
     * 50 attempts per minute per IP (for general API endpoints)
     */
    public static Bucket createApiLimitBucket() {
        Bandwidth limit = Bandwidth.classic(50, Refill.intervally(50, Duration.ofMinutes(1)));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }
}
```

---

## Step 3: Create Rate Limiting Filter

**File**: `src/main/java/org/example/nabat/adapter/in/ratelimiting/RateLimitingFilter.java`

```java
package org.example.nabat.adapter.in.ratelimiting;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        // Apply rate limiting only to auth endpoints
        if ((path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")) 
            && "POST".equals(method)) {
            
            String clientIp = getClientIp(request);
            Bucket bucket = cache.computeIfAbsent(clientIp, k -> RateLimitingConfig.createAuthLimitBucket());
            
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            
            if (probe.isConsumed()) {
                // Request allowed
                response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                // Request denied
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getRoundedSecondsToWait());
                response.addHeader("X-RateLimit-Retry-After-Seconds", String.valueOf(waitForRefill));
                response.sendError(HttpServletResponse.SC_TOO_MANY_REQUESTS, 
                    "Too many authentication attempts. Please try again in " + waitForRefill + " seconds.");
                logger.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, path);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // In case of multiple proxies, take the first IP
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only filter POST requests to auth endpoints
        return !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register");
    }
}
```

**Note**: Add `import java.util.concurrent.TimeUnit;`

---

## Step 4: Add Missing Import

```java
import java.util.concurrent.TimeUnit;
```

---

## Step 5: Register Filter in Security Chain

Update `SecurityConfig.java`:

```java
// In securityFilterChain() method, add the line before the JWT filter:

.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

Inject the filter:
```java
private final RateLimitingFilter rateLimitingFilter;

public SecurityConfig(
    JwtAuthenticationFilter jwtAuthenticationFilter,
    RateLimitingFilter rateLimitingFilter,
    @Value("${nabat.cors.allowed-origins:}") List<String> allowedOrigins
) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.rateLimitingFilter = rateLimitingFilter;
    this.allowedOrigins = allowedOrigins;
}
```

---

## Step 6: Create Login Attempt Audit Entity

**File**: `src/main/java/org/example/nabat/domain/model/LoginAttempt.java`

```java
package org.example.nabat.domain.model;

import java.time.Instant;

public record LoginAttempt(
    Long id,
    String email,
    String clientIp,
    String userAgent,
    boolean success,
    String failureReason,
    Instant attemptedAt
) {
    public static LoginAttempt create(
        String email,
        String clientIp,
        String userAgent,
        boolean success,
        String failureReason
    ) {
        return new LoginAttempt(
            null, // ID auto-generated
            email,
            clientIp,
            userAgent,
            success,
            failureReason,
            Instant.now()
        );
    }
}
```

---

## Step 7: Create JPA Entity for Persistence

**File**: `src/main/java/org/example/nabat/adapter/out/persistence/LoginAttemptJpaEntity.java`

```java
package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "login_attempts", indexes = {
    @Index(name = "idx_email_timestamp", columnList = "email,attempted_at DESC"),
    @Index(name = "idx_client_ip_timestamp", columnList = "client_ip,attempted_at DESC")
})
public class LoginAttemptJpaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false, name = "client_ip")
    private String clientIp;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(nullable = false)
    private boolean success;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @Column(nullable = false, name = "attempted_at")
    private Instant attemptedAt;
    
    // Constructors, getters, setters omitted for brevity
}
```

---

## Step 8: Create Repository Interface

**File**: Application port for audit logging

```java
package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.LoginAttempt;

public interface LoginAttemptRepository {
    LoginAttempt save(LoginAttempt loginAttempt);
    
    int countFailedAttemptsForEmailInLastHour(String email);
    
    int countFailedAttemptsForIpInLastHour(String clientIp);
    
    void deleteOlderThan(java.time.Instant instant); // Cleanup old records
}
```

---

## Step 9: Update Authentication Service

Add audit logging to `AuthenticationService.java`:

```java
@UseCase
public class AuthenticationService implements RegisterUserUseCase, LoginUserUseCase, RefreshTokenUseCase {
    
    // ... existing fields ...
    private final LoginAttemptRepository loginAttemptRepository;
    
    public AuthenticationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        TokenProvider tokenProvider,
        LoginAttemptRepository loginAttemptRepository  // Add this
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginAttemptRepository = loginAttemptRepository;
    }
    
    @Override
    @Transactional(readOnly = true)
    public LoginUserUseCase.LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        
        if (!passwordEncoder.matches(command.password(), user.password())) {
            // Log failed attempt
            loginAttemptRepository.save(LoginAttempt.create(
                command.email(),
                null, // IP should be extracted from request in filter
                null, // User agent should be extracted from request
                false,
                "Invalid password"
            ));
            throw new BadCredentialsException("Invalid email or password");
        }
        
        if (!user.enabled()) {
            loginAttemptRepository.save(LoginAttempt.create(
                command.email(),
                null,
                null,
                false,
                "User account disabled"
            ));
            throw new BadCredentialsException("User account is disabled");
        }
        
        // Log successful attempt
        loginAttemptRepository.save(LoginAttempt.create(
            command.email(),
            null,
            null,
            true,
            null
        ));
        
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        
        return new LoginUserUseCase.LoginResult(
            accessToken,
            refreshToken,
            tokenProvider.getJwtExpiration(),
            user
        );
    }
}
```

---

## Step 10: Add Configuration Properties

Add to `src/main/resources/application.properties`:

```properties
# Rate Limiting
nabat.rate-limit.auth.max-attempts=5
nabat.rate-limit.auth.window-minutes=15
nabat.rate-limit.api.max-attempts=50
nabat.rate-limit.api.window-minutes=1

# Login Attempt Cleanup (run daily)
nabat.login-attempt.cleanup-days-old=30
```

---

## Step 11: Create Database Migration

**File**: `src/main/resources/db/migration/V3__create_login_attempts_table.sql`

```sql
CREATE TABLE login_attempts (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    client_ip VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    attempted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_timestamp ON login_attempts(email, attempted_at DESC);
CREATE INDEX idx_client_ip_timestamp ON login_attempts(client_ip, attempted_at DESC);
CREATE INDEX idx_attempted_at ON login_attempts(attempted_at DESC);
```

---

## Step 12: Testing

**File**: `src/test/java/org/example/nabat/adapter/in/ratelimiting/RateLimitingFilterIntegrationTest.java`

```java
package org.example.nabat.adapter.in.ratelimiting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingFilterIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldBlockExcessiveLoginAttempts() throws Exception {
        String loginPayload = """
            {
                "email": "test@example.com",
                "password": "password123"
            }
            """;
        
        // First 5 attempts should succeed (or fail with 401/403, not 429)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload)
                .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isNotEqualTo(429));
        }
        
        // 6th attempt should be rate limited (429 Too Many Requests)
        mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload)
            .header("X-Forwarded-For", "192.168.1.100"))
            .andExpect(status().isTooManyRequests());
    }
}
```

---

## Configuration Summary

| Setting | Value | Notes |
|---------|-------|-------|
| Max login attempts | 5 | Per IP per 15 minutes |
| Lockout duration | 15 minutes | Auto-resets |
| Failed login logging | Enabled | For audit trail |
| User enumeration | Prevented | Same error for all cases |
| Response header | `X-RateLimit-Retry-After-Seconds` | Informs client when to retry |

---

## Response Examples

### Successful Login (No Rate Limit)
```
HTTP 200 OK
X-RateLimit-Remaining: 4
Content-Type: application/json
{...accessToken...}
```

### Rate Limited Response
```
HTTP 429 Too Many Requests
X-RateLimit-Retry-After-Seconds: 623
Content-Type: application/json
{
    "error": "Too many authentication attempts. Please try again in 623 seconds."
}
```

---

## For Production Deployment

1. Use **Redis-backed Bucket4j** for distributed rate limiting:
   ```java
   ProxyManager<String> buckets = Bucket4j.extension(RedisProxyManagerFactory.class)
       .getProxyManager("login-limits");
   ```

2. Set up **monitoring/alerting** on rate limit violations

3. Implement **WAF rules** in load balancer/reverse proxy

4. Consider **CDN-level protection** (Cloudflare, AWS WAF)

5. Add **CAPTCHA** integration after repeated failures

---

## References

- Bucket4j: https://github.com/vladimir-bukhtoyarov/bucket4j
- OWASP Rate Limiting: https://cheatsheetseries.owasp.org/cheatsheets/Brute_Force_Protection_Cheat_Sheet.html
- Spring Security: https://spring.io/projects/spring-security


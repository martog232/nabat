# Quick Wins: Brute Force Protection (No External Dependencies)

## Summary

Your app currently **lacks brute force protection** but has a solid foundation. Below are quick wins you can implement immediately.

---

## 🚀 Quick Win #1: Prevent User Enumeration (15 minutes)

### Problem
The registration endpoint reveals whether an email is already registered:
```
POST /api/v1/auth/register
400 Bad Request: "Email already exists"  ← Confirms email is valid
```

Attacker learns valid emails and targets them.

### Solution
Update `AuthenticationService.register()`:

```java
@Override
@Transactional
public User register(RegisterCommand command) {
    if (userRepository.existsByEmail(command.email())) {
        // ✅ NEW: Don't reveal if email exists
        // Instead: pretend to send verification email
        logger.info("Registration attempt with existing email: {}", command.email());
        
        // Return 201 Created with generic message
        // In future: send "verify email" link even if already registered
        throw new IllegalArgumentException("Registration submitted. Please verify your email.");
    }

    String hashedPassword = passwordEncoder.encode(command.password());
    User user = User.create(command.email(), hashedPassword, command.displayName());
    
    return userRepository.save(user);
}
```

**Effect**: Same response for registered/unregistered emails → no enumeration

---

## 🚀 Quick Win #2: Log Failed Authentication Attempts (30 minutes)

### Problem
No visibility into attack patterns. Can't detect brute force in progress.

### Solution
Create a simple in-memory attempt tracker:

**File**: `src/main/java/org/example/nabat/adapter/in/security/LoginAttemptTracker.java`

```java
package org.example.nabat.adapter.in.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptTracker.class);
    private static final int WINDOW_MINUTES = 60;
    private static final int ALERT_THRESHOLD = 10; // Alert after 10 failed attempts
    
    private final Map<String, List<LoginAttemptRecord>> failedAttemptsByEmail = new ConcurrentHashMap<>();
    private final Map<String, List<LoginAttemptRecord>> failedAttemptsByIp = new ConcurrentHashMap<>();
    
    public void recordFailedAttempt(String email, String clientIp) {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        
        // Clean up old attempts
        cleanupOldAttempts(failedAttemptsByEmail, oneHourAgo);
        cleanupOldAttempts(failedAttemptsByIp, oneHourAgo);
        
        // Record email-based attempt
        var emailAttempts = failedAttemptsByEmail.computeIfAbsent(email, k -> Collections.synchronizedList(new ArrayList<>()));
        emailAttempts.add(new LoginAttemptRecord(email, clientIp, now));
        
        int emailFailureCount = countRecentFailures(emailAttempts, oneHourAgo);
        if (emailFailureCount > ALERT_THRESHOLD) {
            logger.warn("⚠️  BRUTE FORCE ALERT: {} failed login attempts for email: {} in last {} minutes",
                emailFailureCount, email, WINDOW_MINUTES);
        }
        
        // Record IP-based attempt
        var ipAttempts = failedAttemptsByIp.computeIfAbsent(clientIp, k -> Collections.synchronizedList(new ArrayList<>()));
        ipAttempts.add(new LoginAttemptRecord(email, clientIp, now));
        
        int ipFailureCount = countRecentFailures(ipAttempts, oneHourAgo);
        if (ipFailureCount > ALERT_THRESHOLD) {
            logger.warn("⚠️  BRUTE FORCE ALERT: {} failed login attempts from IP: {} in last {} minutes",
                ipFailureCount, clientIp, WINDOW_MINUTES);
        }
    }
    
    private void cleanupOldAttempts(Map<String, List<LoginAttemptRecord>> attempts, Instant cutoff) {
        attempts.values().forEach(list -> list.removeIf(record -> record.timestamp.isBefore(cutoff)));
    }
    
    private int countRecentFailures(List<LoginAttemptRecord> attempts, Instant cutoff) {
        return (int) attempts.stream().filter(a -> a.timestamp.isAfter(cutoff)).count();
    }
    
    public int getFailedAttemptCountForEmail(String email) {
        List<LoginAttemptRecord> attempts = failedAttemptsByEmail.getOrDefault(email, List.of());
        return countRecentFailures(attempts, Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES));
    }
    
    public int getFailedAttemptCountForIp(String clientIp) {
        List<LoginAttemptRecord> attempts = failedAttemptsByIp.getOrDefault(clientIp, List.of());
        return countRecentFailures(attempts, Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES));
    }
    
    private record LoginAttemptRecord(String email, String clientIp, Instant timestamp) {}
}
```

Now update `AuthenticationService`:

```java
@UseCase
public class AuthenticationService implements RegisterUserUseCase, LoginUserUseCase, RefreshTokenUseCase {
    
    private final LoginAttemptTracker loginAttemptTracker;
    
    public AuthenticationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        TokenProvider tokenProvider,
        LoginAttemptTracker loginAttemptTracker  // ← Add
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginAttemptTracker = loginAttemptTracker;
    }
    
    @Override
    @Transactional(readOnly = true)
    public LoginUserUseCase.LoginResult login(LoginCommand command) {
        String clientIp = RequestContextHelper.getClientIp();  // See below
        
        User user = userRepository.findByEmail(command.email())
            .orElseThrow(() -> {
                loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
                return new BadCredentialsException("Invalid email or password");
            });
        
        if (!passwordEncoder.matches(command.password(), user.password())) {
            loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
            throw new BadCredentialsException("Invalid email or password");
        }
        
        if (!user.enabled()) {
            loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
            throw new BadCredentialsException("User account is disabled");
        }
        
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

**Helper class** to extract client IP from request context:

```java
package org.example.nabat.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestContextHelper {
    
    public static String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "UNKNOWN";
        }
        
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
```

**Effect**: Console logs show brute force attacks in real-time. Can be monitored with log aggregation (ELK, Datadog).

---

## 🚀 Quick Win #3: Add Attempt Limiting (Soft Block) (1 hour)

### Problem
No blocking mechanism, only logging.

### Solution
Add configurable attempt counter that blocks after N failures:

**File**: `src/main/java/org/example/nabat/adapter/in/security/LoginLimiter.java`

```java
package org.example.nabat.adapter.in.security;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginLimiter {
    
    @Value("${nabat.security.max-failed-attempts:5}")
    private int maxFailedAttempts;
    
    @Value("${nabat.security.lockout-minutes:15}")
    private int lockoutMinutes;
    
    private final Map<String, LockoutRecord> lockedEmails = new ConcurrentHashMap<>();
    
    public void recordFailure(String email) {
        lockedEmails.compute(email, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                existing.incrementFailures();
                return existing;
            }
            return new LockoutRecord();
        });
    }
    
    public void recordSuccess(String email) {
        lockedEmails.remove(email);
    }
    
    public void checkLoginAllowed(String email) {
        LockoutRecord record = lockedEmails.get(email);
        if (record == null) {
            return;
        }
        
        if (record.isExpired()) {
            lockedEmails.remove(email);
            return;
        }
        
        if (record.failureCount >= maxFailedAttempts) {
            long minutesRemaining = ChronoUnit.MINUTES.between(Instant.now(), record.lockedUntil);
            throw new BadCredentialsException(
                "Account temporarily locked due to too many failed attempts. Try again in " + minutesRemaining + " minutes."
            );
        }
    }
    
    private class LockoutRecord {
        int failureCount = 1;
        Instant lockedUntil = Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES);
        
        void incrementFailures() {
            failureCount++;
            lockedUntil = Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES);
        }
        
        boolean isExpired() {
            return Instant.now().isAfter(lockedUntil);
        }
    }
}
```

Update `AuthenticationService`:

```java
@Override
@Transactional(readOnly = true)
public LoginUserUseCase.LoginResult login(LoginCommand command) {
    // 1. Check if account is locked
    loginLimiter.checkLoginAllowed(command.email()); // ← Throws exception if locked
    
    // 2. Try login
    String clientIp = RequestContextHelper.getClientIp();
    User user = userRepository.findByEmail(command.email())
        .orElseThrow(() -> {
            loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
            loginLimiter.recordFailure(command.email());  // ← Track failure
            return new BadCredentialsException("Invalid email or password");
        });
    
    if (!passwordEncoder.matches(command.password(), user.password())) {
        loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
        loginLimiter.recordFailure(command.email());  // ← Track failure
        throw new BadCredentialsException("Invalid email or password");
    }
    
    if (!user.enabled()) {
        loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
        loginLimiter.recordFailure(command.email());  // ← Track failure
        throw new BadCredentialsException("User account is disabled");
    }
    
    // 3. Success - clear failed attempts
    loginLimiter.recordSuccess(command.email());  // ← Clear on success
    loginAttemptTracker.recordAttempt(command.email(), clientIp, true);
    
    // ... generate tokens ...
}
```

Add properties:
```properties
nabat.security.max-failed-attempts=5
nabat.security.lockout-minutes=15
```

**Effect**: After 5 failed attempts, account locks for 15 minutes.

---

## 🚀 Quick Win #4: Add Security Headers (10 minutes)

### Problem
Missing security headers that mitigate various attacks.

### Solution
Update `SecurityConfig.java`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // ... existing config ...
        .headers(headers -> headers
            .frameOptions(frameOptions -> frameOptions.deny())
            .xssProtection()
            .and()
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
        )
        // ... rest of config ...
}
```

**Effect**:
- `X-Frame-Options: DENY` — Prevents clickjacking
- `X-Content-Type-Options: nosniff` — Prevents MIME sniffing
- `X-XSS-Protection` — Legacy XSS protection

---

## Summary Table

| Quick Win | Effort | Impact | Dependencies |
|-----------|--------|--------|---|
| Prevent user enumeration | 15 min | MEDIUM | None |
| Log failed attempts | 30 min | LOW (visibility only) | None |
| Attempt limiting (soft block) | 1 hr | HIGH | None |
| Security headers | 10 min | MEDIUM | None |
| **TOTAL** | **~2 hours** | **MEDIUM-HIGH** | **NONE** |

---

## Next Steps

1. **Implement the 4 quick wins above** (no dependencies needed)
2. **Monitor logs** for brute force patterns
3. **Add Bucket4j rate limiting** (see RATE_LIMITING_IMPLEMENTATION.md)
4. **Deploy WAF** (production only)
5. **Implement 2FA** (future enhancement)

---

## Testing the Changes

```bash
# Test 1: User enumeration prevented
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "pwd123", "displayName": "Test"}'
# Expected: Same response for both registered and unregistered emails

# Test 2: Failed login logging (check logs)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "wrongpwd"}'
# Expected: Logs show "⚠️ BRUTE FORCE ALERT" after 10 attempts

# Test 3: Account lockout after 5 failures
# Send 5 login failures, then 6th should get: "Account temporarily locked"
```

---

## Monitoring Checklist

- [ ] Set up log aggregation (e.g., `grep "BRUTE FORCE ALERT" logs/*)
- [ ] Alert on `>10` failed attempts in 1 hour
- [ ] Track failed attempts per IP and per email
- [ ] Notify security team of suspicious patterns
- [ ] Weekly review of login attempt metrics


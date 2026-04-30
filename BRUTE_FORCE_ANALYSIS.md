# Brute Force Attack Protection Analysis - Nabat App

## Current Security Posture

### ✅ What's Already Protected

1. **Password Security**
   - Uses **BCrypt password hashing** (strong, adaptive hashing algorithm)
   - Passwords are never stored in plaintext
   - Password verification uses secure comparison via `passwordEncoder.matches()`
   - Located in: `AuthenticationService.java` and `SecurityConfig.java`

2. **JWT Token Security**
   - Access tokens carry secure claims (`userId`, `email`, `role`, `tokenType`)
   - Token type enforcement: refresh tokens are rejected for API authentication
   - Only access tokens allowed for API requests
   - Disabling users prevents authentication even with valid tokens
   - Located in: `JwtAuthenticationFilter.java` and `JwtTokenProvider.java`

3. **Session Management**
   - Stateless session policy (JWT-based, no session cookies)
   - CSRF protection disabled (appropriate for stateless JWT)
   - Security context properly cleared on authentication failures
   - Located in: `SecurityConfig.java` and `JwtAuthenticationFilter.java`

4. **Input Validation**
   - Login/register endpoints validate email format and required fields
   - Uses Jakarta validation annotations (`@Email`, `@NotBlank`)
   - Located in: `LoginRequest.java` and `RegisterRequest.java`

---

## ⚠️ Current Vulnerabilities to Brute Force Attacks

### 1. **NO RATE LIMITING on Authentication Endpoints**
   - **Impact**: HIGH
   - **Vulnerable Endpoints**:
     - `POST /api/v1/auth/login` — Can attempt unlimited password guesses
     - `POST /api/v1/auth/register` — Can enumerate valid emails at unlimited speed
   - **Attack Scenario**: Attacker sends thousands of login requests per second trying different passwords
   - **Current Status**: Marked as **Task T-44** in TASKS.md (pending implementation)

### 2. **NO ACCOUNT LOCKOUT MECHANISM**
   - **Impact**: HIGH
   - **Details**: Failed login attempts don't trigger account lockout
   - **Attack Scenario**: Brute force all passwords against a known username without account disabling
   - **Users have no visibility** into failed login attempts

### 3. **NO LOGIN ATTEMPT LOGGING/MONITORING**
   - **Impact**: MEDIUM
   - **Details**: Failed authentication attempts are not logged with client IP/timing
   - **Missing**: Audit trail for security incident investigation
   - **Attack Detection**: No automated detection of suspicious patterns (e.g., 50 failed attempts in 1 minute)

### 4. **NO IP-BASED THROTTLING**
   - **Impact**: HIGH
   - **Details**: Same origin can hammer endpoints without limits
   - **Attack Scenario**: A single attacker IP can make unlimited requests

### 5. **USER ENUMERATION IS POSSIBLE**
   - **Impact**: MEDIUM
   - **Details**: 
     - Login endpoint returns "Invalid email or password" for both non-existent emails and wrong passwords
     - However, registration endpoint confirms if email is already registered
     - Attacker can learn valid email addresses and target them specifically
   - **Current**: Found in `AuthenticationService.login()` — returns same error for both cases (✓ good)
   - **But `register()` throws**: `"Email already exists"` (✗ bad)

---

## ⛑️ Recommended Protections

### Priority 1: HIGH - Address Critical Gaps

#### 1. **Implement Rate Limiting (Recommended Library: Bucket4j)**

**Per-IP Rate Limiting for Login/Register:**
```
- Max 5 attempts per IP per 15 minutes
- Max 20 attempts per IP per hour
- Sliding window strategy
```

**Per-User Rate Limiting for Login:**
```
- Max 10 failed attempts per email per hour
- After 10 failures, lock account for 30 minutes
- Send email notification to owner
```

#### 2. **Add Account Lockout**

After N failed attempts:
- Temporarily disable account (30-60 minutes)
- Require email verification or admin unlock
- Notify user of suspicious activity

#### 3. **Implement Failed Login Attempt Tracking**

Track per user:
- Failed attempt count
- Last attempt timestamp
- IP address of attacker
- User agent string

#### 4. **Preventive User Enumeration**

Update registration response:
```java
// Instead of:
// throw new IllegalArgumentException("Email already exists");

// Return:
// 200 OK "Please check your email to verify your account" 
// (even if email already registered)
```

### Priority 2: MEDIUM - Defense in Depth

#### 1. **CAPTCHA on Repeated Failures**
- After 3 failed attempts from same IP, require CAPTCHA
- Reset counter on successful completion

#### 2. **Suspicious Activity Alerts**
- Alert user after 3 consecutive failed login attempts
- Include IP address and timestamp in email

#### 3. **API Gateway / Web Application Firewall (WAF)**
- Deploy WAF (e.g., AWS WAF, Cloudflare) in front of application
- Block requests with suspicious patterns
- Geographic rate limiting

#### 4. **Distributed Rate Limiting (for Microservices)**
- Use Redis-backed rate limiter (works across multiple instances)
- Prevents attackers from bypassing by hitting different servers

### Priority 3: LOW - Advanced Monitoring

#### 1. **Login Attempt Analytics**
- Dashboard showing failed login trends
- Alert on unusual patterns (geographic location, device, time-of-day)

#### 2. **Implement 2FA (Two-Factor Authentication)**
- Time-based OTP (TOTP) via authenticator apps
- Recovery codes for account recovery

---

## Implementation Summary

| Feature | Status | Priority | Est. Effort |
|---------|--------|----------|-------------|
| Rate Limiting (Bucket4j) | ❌ Not implemented | HIGH | 4-6 hours |
| Account Lockout | ❌ Not implemented | HIGH | 3-4 hours |
| Failed Login Audit Trail | ❌ Not implemented | HIGH | 4-5 hours |
| IP-based Throttling | ❌ Not implemented | HIGH | 3-4 hours |
| User Enumeration Prevention | ⚠️ Partial | MEDIUM | 2 hours |
| CAPTCHA Integration | ❌ Not implemented | MEDIUM | 6-8 hours |
| Suspicious Activity Alerts | ❌ Not implemented | MEDIUM | 5-6 hours |
| 2FA Support | ❌ Not implemented | LOW | 8-10 hours |

---

## Attack Scenarios & Impact

### Scenario 1: Direct Password Brute Force
**Attack**: Attacker performs 1,000 login attempts/minute against known user email
**Current Protection**: ❌ None - BCrypt slows down attempts but no rate limiting
**With Fixes**: ✅ Blocked after 5 failed attempts

### Scenario 2: Distributed Brute Force
**Attack**: Multiple IPs attempt logins (botnet)
**Current Protection**: ⚠️ BCrypt CPU-intensive, but no per-user tracking
**With Fixes**: ✅ Account locked after N attempts, user notified

### Scenario 3: Email Enumeration
**Attack**: Attacker finds valid emails via registration endpoint logic
**Current Protection**: ⚠️ Login endpoint hides emails, but register confirms them
**With Fixes**: ✅ Same response for registered/unregistered emails

### Scenario 4: Timing Attacks on Password Validation
**Attack**: Attacker measures response time to guess password length
**Current Protection**: ✅ BCrypt consistent timing (resistant to timing attacks)
**With Fixes**: ✅ Already protected

---

## Code Architecture for Rate Limiting

**Suggested Implementation**:
```
adapter/in/rate_limiting/
├── RateLimitingFilter.java (intercepts login/register)
├── RateLimitStore.java (interface)
└── RedisRateLimitStore.java (implementation)

domain/model/
├── LoginAttempt.java (audit entity)
└── LoginAttemptRepository.java (persistence port)
```

---

## Quick Hardening Checklist

- [ ] Add Bucket4j to `pom.xml`
- [ ] Create `LoginAttempt` domain entity with JPA persistence
- [ ] Implement `RateLimitingFilter` in security chain
- [ ] Add `@PreAuthorize` for admin endpoints (T-15 in TASKS.md)
- [ ] Update `register()` endpoint to prevent user enumeration
- [ ] Add integration tests for rate limiting behavior
- [ ] Document rate limit thresholds in `application.properties`
- [ ] Add metrics/monitoring for failed attempts
- [ ] Set up email alerts for suspicious activity
- [ ] Deploy WAF in production

---

## References

- **OWASP**: https://owasp.org/www-community/attacks/Brute_force_attack
- **Spring Security**: https://spring.io/projects/spring-security
- **Bucket4j**: https://github.com/vladimir-bukhtoyarov/bucket4j
- **NIST Guidelines**: SP 800-63B (Authentication)

---

## Related Tasks in TASKS.md

- **T-44** 🟠 Rate limit `/auth/login` and `/auth/register` (P3, pending)
- **T-15** 🟠 Role-based authorization with `@PreAuthorize` (P1, pending)
- **T-45** Email verification & password reset flow (P3, pending)


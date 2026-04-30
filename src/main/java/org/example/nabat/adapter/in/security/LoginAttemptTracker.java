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

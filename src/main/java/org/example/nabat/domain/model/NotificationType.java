package org.example.nabat.domain.model;

public enum NotificationType {
    ALERT_UPVOTED,       // Някой upvote-на твоя alert
    ALERT_DOWNVOTED,     // Някой downvote-на твоя alert
    ALERT_CONFIRMED,     // Някой потвърди твоя alert
    ALERT_MILESTONE,     // Alert достигна milestone (10, 50, 100 потвърждения)
    ALERT_RESOLVED       // Твой alert беше маркиран като resolved

}

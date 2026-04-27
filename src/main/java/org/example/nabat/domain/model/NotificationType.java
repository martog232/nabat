package org.example.nabat.domain.model;

public enum NotificationType {
    ALERT_UPVOTED,    // someone upvoted your alert
    ALERT_DOWNVOTED,  // someone downvoted your alert
    ALERT_CONFIRMED,  // someone confirmed your alert (on-site)
    ALERT_MILESTONE,  // alert reached a confirmation milestone (10, 50, 100, ...)
    ALERT_RESOLVED    // your alert was marked as resolved
}

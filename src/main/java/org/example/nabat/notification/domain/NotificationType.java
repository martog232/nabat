package org.example.nabat.notification.domain;

public enum NotificationType {
    ALERT_UPVOTED,    // someone upvoted your alert
    ALERT_DOWNVOTED,  // someone downvoted your alert
    ALERT_CONFIRMED,  // someone confirmed your alert (on-site)
    ALERT_MILESTONE,  // alert reached a confirmation milestone (10, 50, 100, ...)
    ALERT_RESOLVED,   // your alert was marked as resolved
    COMMENT_ADDED,    // someone wrote in the thread of a report you reported or commented on
    REPORT_FORWARDED, // your report went to another authority, which says it is theirs
    REPORT_PROGRESSED // the authority acknowledged your report, started work on it, or rejected it
}

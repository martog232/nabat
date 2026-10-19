package org.example.nabat.jurisdiction.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivers a report to a district administration.
 *
 * <p>Unlike the account mails in {@code identity}, which log a failed send and carry on,
 * this <strong>must throw</strong> when delivery fails. The caller is a durable listener:
 * an exception is what leaves its publication outstanding so the report is sent again.
 * A swallowed failure here is a report the district never receives, with every record in
 * nabat saying it was delivered.
 */
public interface SignalMailer {

    void send(SignalMail mail);

    /**
     * Everything a district needs to act on a report, and to answer the person who made it.
     *
     * @param reporterName  the citizen's display name. A report to an administration is
     *                      considered only when its author is identifiable, so it is sent
     * @param reporterEmail shown with the name, for the same reason. Not the Reply-To: the
     *                      district answers in nabat, where the reporter and everyone else
     *                      following the report can see the answer
     * @param categoryLabel the category in words, group and subcategory:
     *                      „Пътища и шахти — Улично платно“
     */
    record SignalMail(
        UUID alertId,
        String to,
        String districtName,
        String categoryLabel,
        String title,
        String description,
        double latitude,
        double longitude,
        Instant reportedAt,
        String reporterName,
        String reporterEmail
    ) {
    }
}

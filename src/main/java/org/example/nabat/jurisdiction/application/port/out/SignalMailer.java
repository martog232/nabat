package org.example.nabat.jurisdiction.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivers a report to the authority that answers for it: a district administration, or an
 * institution.
 *
 * <p>Unlike the account mails in {@code identity}, which log a failed send and carry on,
 * this <strong>must throw</strong> when delivery fails. The caller is a durable listener:
 * an exception is what leaves its publication outstanding so the report is sent again.
 * A swallowed failure here is a report the authority never receives, with every record in
 * nabat saying it was delivered.
 */
public interface SignalMailer {

    void send(SignalMail mail);

    /**
     * Everything an authority needs to act on a report, and to answer the person who made it.
     *
     * @param recipientName the authority the report goes to: a district administration, or an
     *                      institution its category belongs to
     * @param districtName  the district the report is in, which for an institution is part of
     *                      saying where
     * @param reporterName  the citizen's display name. A report to an administration is
     *                      considered only when its author is identifiable, so it is sent
     * @param reporterEmail shown with the name, for the same reason. Not the Reply-To: the
     *                      authority answers in nabat, where the reporter and everyone else
     *                      following the report can see the answer
     * @param categoryLabel the category in words, group and subcategory:
     *                      „Пътища и шахти — Улично платно“
     * @param note          said first, for any mail but a new report's: who forwarded it and why,
     *                      or how long it has been waiting; null for a new report
     * @param signInKey     the secret of a link that signs the authority's official in, behind
     *                      the button; null when the authority has no account to sign in to
     * @param kind          what the mail is, which its subject says
     */
    record SignalMail(
        UUID alertId,
        String to,
        String recipientName,
        String districtName,
        String categoryLabel,
        String title,
        String description,
        double latitude,
        double longitude,
        Instant reportedAt,
        String reporterName,
        String reporterEmail,
        String note,
        String signInKey,
        Kind kind
    ) {

        public enum Kind {
            /** The report, as it was made. */
            NEW,
            /** Sent on by another authority. */
            FORWARDED,
            /** Its deadline passed with no answer: the authority is reminded. */
            REMINDER,
            /** Still no answer after the reminder: the municipality is told. */
            ESCALATION,
            /** Its reporter took it back: the authority can stop. */
            WITHDRAWN
        }

        /** A new report, or a forwarded one when there is a note — the two the dispatch sends. */
        public SignalMail(
            UUID alertId, String to, String recipientName, String districtName, String categoryLabel,
            String title, String description, double latitude, double longitude, Instant reportedAt,
            String reporterName, String reporterEmail, String note, String signInKey
        ) {
            this(alertId, to, recipientName, districtName, categoryLabel, title, description, latitude, longitude,
                reportedAt, reporterName, reporterEmail, note, signInKey, note == null ? Kind.NEW : Kind.FORWARDED);
        }
    }
}

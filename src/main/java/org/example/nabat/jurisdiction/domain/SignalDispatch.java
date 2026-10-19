package org.example.nabat.jurisdiction.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A report delivered to the authority that answers for it: to whom, and when.
 *
 * <p>Kept because delivery is retried. Without a record of what was sent, a replay after a
 * crash cannot tell "sent, but not yet marked" from "never sent", and the only safe choice
 * would be to send again.
 *
 * @param sequence      0 for the mail when the report was made; n for the mail to whoever the
 *                      n-th forward named (V30)
 * @param districtCode  where the report is
 * @param authorityCode who it went to: the district, or an institution (V29)
 */
public record SignalDispatch(
    UUID alertId, int sequence, String districtCode, String authorityCode, String recipient, Instant sentAt) {
}

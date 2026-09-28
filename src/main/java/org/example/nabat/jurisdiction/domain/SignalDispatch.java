package org.example.nabat.jurisdiction.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A report delivered to a district: to whom, and when.
 *
 * <p>Kept because delivery is retried. Without a record of what was sent, a replay after a
 * crash cannot tell "sent, but not yet marked" from "never sent", and the only safe choice
 * would be to send again.
 */
public record SignalDispatch(UUID alertId, String districtCode, String recipient, Instant sentAt) {
}

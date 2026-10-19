package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.example.nabat.jurisdiction.application.port.out.SignalDispatchRepository;
import org.example.nabat.jurisdiction.domain.SignalDispatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Deliveries, in plain SQL.
 *
 * <p>Was a JPA entity keyed by the report alone. With forwards (V30) the key is the report and
 * the delivery's sequence, and a composite key in JPA is an id class for two columns that
 * only ever get inserted and looked up. An INSERT also says what this table is for without a
 * comment: a second dispatch of the same delivery fails on the primary key rather than being
 * merged over the first, which is what {@code isNew()} had to be overridden to get.
 */
@Component
public class SignalDispatchRepositoryAdapter implements SignalDispatchRepository {

    private final JdbcTemplate jdbc;

    public SignalDispatchRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UUID alertId, int sequence) {
        Boolean found = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM signal_dispatches WHERE alert_id = ? AND sequence = ?)",
            Boolean.class, alertId, sequence);
        return Boolean.TRUE.equals(found);
    }

    @Override
    public void save(SignalDispatch d) {
        jdbc.update("""
            INSERT INTO signal_dispatches (alert_id, sequence, district_code, authority_code, recipient, sent_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            d.alertId(), d.sequence(), d.districtCode(), d.authorityCode(), d.recipient(),
            d.sentAt().atOffset(ZoneOffset.UTC));
    }
}

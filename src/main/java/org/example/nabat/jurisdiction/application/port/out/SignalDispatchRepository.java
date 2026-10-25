package org.example.nabat.jurisdiction.application.port.out;

import org.example.nabat.jurisdiction.domain.SignalDispatch;

import java.util.UUID;

public interface SignalDispatchRepository {

    /** Whether this delivery — the creation mail, {@code 0}, or the n-th forward's — went. */
    boolean exists(UUID alertId, int sequence);

    /** Always an insert: a second record of one delivery is the bug this table catches. */
    void save(SignalDispatch dispatch);
}

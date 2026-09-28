package org.example.nabat.jurisdiction.application.port.out;

import org.example.nabat.jurisdiction.domain.SignalDispatch;

import java.util.UUID;

public interface SignalDispatchRepository {

    boolean existsForAlert(UUID alertId);

    void save(SignalDispatch dispatch);
}

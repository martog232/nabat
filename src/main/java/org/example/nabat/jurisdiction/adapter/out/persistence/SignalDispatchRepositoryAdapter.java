package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.example.nabat.jurisdiction.application.port.out.SignalDispatchRepository;
import org.example.nabat.jurisdiction.domain.SignalDispatch;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SignalDispatchRepositoryAdapter implements SignalDispatchRepository {

    private final SignalDispatchJpaRepository jpaRepository;

    public SignalDispatchRepositoryAdapter(SignalDispatchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsForAlert(UUID alertId) {
        return jpaRepository.existsById(alertId);
    }

    /**
     * Always an insert — see {@link SignalDispatchJpaEntity#isNew()}. The entity has an
     * assigned id, so a plain {@code save} would look for an existing row and quietly
     * overwrite it. A second dispatch for one report is exactly the bug the table exists
     * to catch, and should fail on the primary key.
     */
    @Override
    public void save(SignalDispatch dispatch) {
        jpaRepository.saveAndFlush(SignalDispatchJpaEntity.from(dispatch));
    }
}

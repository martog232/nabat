package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.AlertId;

/**
 * The report has been delivered to the institution responsible for it.
 *
 * <p>Called by whoever does the delivering, which is not this module: incident knows that a
 * report goes somewhere, not that it goes by email.
 */
public interface RecordDeliveryUseCase {

    /**
     * Moves the report from {@code SUBMITTED} to {@code SENT}, if {@code authorityCode} is still
     * the authority it is waiting on. A delivery to an authority the report has since been
     * forwarded away from (V30) says nothing about whether the new one has it.
     *
     * <p>Idempotent, unlike the other transitions, and on purpose. Delivery is retried, so the
     * same report can be recorded twice; and a report can move on before delivery is recorded
     * — resolved by its reporter while the mail was on its way. Neither is an error, and
     * neither should drag a report back to {@code SENT}. Anything past {@code SUBMITTED} is
     * left as it is.
     *
     * @throws org.example.nabat.incident.domain.AlertNotFoundException if no such report
     */
    void recordDelivered(AlertId id, String authorityCode);
}

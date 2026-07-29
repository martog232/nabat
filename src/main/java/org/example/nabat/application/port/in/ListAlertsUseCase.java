package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertStatus;

import java.util.List;

/**
 * Administrative listing of alerts by lifecycle status.
 *
 * <p>Exists so {@code AlertController} does not have to inject the
 * {@code AlertRepository} out-port to serve this endpoint. A controller wired
 * straight to a repository skips the application layer, which is where
 * authorization, transactions and any future paging belong.
 */
public interface ListAlertsUseCase {

    List<Alert> listByStatus(AlertStatus status);
}

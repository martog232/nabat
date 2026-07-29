package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.application.port.in.ListAlertsUseCase;
import org.example.nabat.application.port.in.ResolveAlertUseCase;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.exception.AlertNotFoundException;
import org.example.nabat.domain.exception.NotAuthorizedException;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class AlertLifecycleService implements GetAlertByIdUseCase, ResolveAlertUseCase, ListAlertsUseCase {

    private final AlertRepository alertRepository;
    private final AlertNotificationPort alertNotificationPort;

    @Override
    @Transactional(readOnly = true)
    public Alert getById(AlertId id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> listByStatus(AlertStatus status) {
        return alertRepository.findByStatus(status);
    }

    /**
     * Resolving removes an alert from the active set, so every cached nearby-alerts
     * result is now wrong. Evicting the whole cache is acceptable because entries are
     * short-lived (15s TTL) and resolves are rare compared with reads — and stale
     * safety alerts are a worse failure than a brief drop in hit rate.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert resolve(AlertId id, User actor) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        boolean isOwner = alert.reportedBy().equals(actor.id().value());
        boolean isAdmin = actor.role() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            // Domain exception, not Spring Security's AccessDeniedException: AGENTS.md
            // forbids framework exceptions here, and the handler maps this to the same 403.
            throw new NotAuthorizedException("Only the reporter or an admin can resolve this alert");
        }

        Alert resolved = alertRepository.save(alert.resolve());
        alertNotificationPort.broadcastAlertUpdate(resolved);
        return resolved;
    }
}


package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.application.port.in.ResolveAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.exception.AlertNotFoundException;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class AlertLifecycleService implements GetAlertByIdUseCase, ResolveAlertUseCase {

    private final AlertRepository alertRepository;

    @Override
    @Transactional(readOnly = true)
    public Alert getById(AlertId id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
    }

    @Override
    @Transactional
    public Alert resolve(AlertId id, User actor) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        boolean isOwner = alert.reportedBy().equals(actor.id().value());
        boolean isAdmin = actor.role() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Only the reporter or an admin can resolve this alert");
        }

        return alertRepository.save(alert.resolve());
    }
}


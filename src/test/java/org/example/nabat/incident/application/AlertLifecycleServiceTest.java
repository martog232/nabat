package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertResolved;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ReportProgressed;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.example.nabat.shared.domain.NotAuthorizedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertLifecycleServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AlertNotificationPort alertNotificationPort;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private ResponsibleAuthorityPort authorities;

    private AlertLifecycleService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AlertLifecycleService(alertRepository, alertNotificationPort, events, authorities,
            new AnswerDeadline(java.time.Duration.ofDays(30)));
        lenient().when(authorities.nameOf("LOZENETS")).thenReturn(Optional.of("Район Лозенец"));
    }

    private Alert active() {
        return new Alert(alertId, "T", "D", 1007,
                Location.of(0, 0), Instant.now(), AlertStatus.SUBMITTED,
                ownerId, 0, 0, 0,
        0, null, null, "LOZENETS", null);
    }

    private User user(UUID id, Role role) {
        return new User(UserId.of(id), "x@y.z", "p", "n", role, true, false, Instant.now(), Instant.now(),
                0, null);
    }

    @Test
    void getById_existing_returns() {
        Alert a = active();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(a));
        assertSame(a, service.getById(alertId));
    }

    @Test
    void getById_missing_throws() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());
        assertThrows(AlertNotFoundException.class, () -> service.getById(alertId));
    }

    @Test
    void resolve_byOwner_succeeds() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Alert result = service.resolve(alertId, user(ownerId, Role.USER));

        assertEquals(AlertStatus.RESOLVED, result.status());
        assertNotNull(result.resolvedAt());
    }

    /** What lets the reporter be asked whether it really is fixed. */
    @Test
    void resolvingAnnouncesTheResolvedReport() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Alert result = service.resolve(alertId, user(UUID.randomUUID(), Role.ADMIN));

        verify(events).publishEvent(new AlertResolved(result));
    }

    @Test
    void aFailedResolveAnnouncesNothing() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));

        assertThrows(NotAuthorizedException.class,
                () -> service.resolve(alertId, user(UUID.randomUUID(), Role.USER)));
        verifyNoInteractions(events);
    }

    @Test
    void resolve_byAdmin_succeeds() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Alert result = service.resolve(alertId, user(UUID.randomUUID(), Role.ADMIN));

        assertEquals(AlertStatus.RESOLVED, result.status());
    }

    @Test
    void resolve_alreadyResolved_throwsIllegalState() {
        Alert resolved = new Alert(alertId, "T", "D", 1007,
                Location.of(0, 0), Instant.now(), AlertStatus.RESOLVED,
                ownerId, 0, 0, 0,
        0, Instant.now(), null, "LOZENETS", null);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(resolved));

        assertThrows(IllegalStateException.class,
                () -> service.resolve(alertId, user(ownerId, Role.USER)));
        verify(alertRepository, never()).save(any());
    }

    @Test
    void resolve_byStranger_throwsAccessDenied() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        assertThrows(NotAuthorizedException.class,
                () -> service.resolve(alertId, user(UUID.randomUUID(), Role.USER)));
        verify(alertRepository, never()).save(any());
    }

    @Test
    void resolve_missing_throwsNotFound() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());
        assertThrows(AlertNotFoundException.class,
                () -> service.resolve(alertId, user(ownerId, Role.USER)));
    }

    // District officials. The report is in LOZENETS throughout.

    private User official(String district) {
        return User.create("official-" + district + "@example.com", "p", "Official").appointTo(district);
    }

    private void saves() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void anOfficialOfTheDistrictMovesItsReportThroughTheLifecycle() {
        saves();
        User lozenets = official("LOZENETS");

        assertEquals(AlertStatus.ACKNOWLEDGED, service.acknowledge(alertId, lozenets).status());
        assertEquals(AlertStatus.IN_PROGRESS, service.startWork(alertId, lozenets).status());
        verify(alertNotificationPort, times(2)).broadcastAlertUpdate(any());
    }

    /** The one rule the whole role exists for: your district, and no other. */
    @Test
    void anOfficialOfAnotherDistrictCanDoNothingToIt() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        User triaditsa = official("TRIADITSA");

        assertThrows(NotAuthorizedException.class, () -> service.acknowledge(alertId, triaditsa));
        assertThrows(NotAuthorizedException.class, () -> service.startWork(alertId, triaditsa));
        assertThrows(NotAuthorizedException.class, () -> service.resolve(alertId, triaditsa));
        verify(alertRepository, never()).save(any());
        verifyNoInteractions(alertNotificationPort);
    }

    /** Acknowledging and starting work are statements only the district can make — not even a moderator. */
    @Test
    void nobodyButTheDistrictAcknowledgesOrStartsWork() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));

        for (User actor : List.of(user(ownerId, Role.USER), user(UUID.randomUUID(), Role.MODERATOR))) {
            assertThrows(NotAuthorizedException.class, () -> service.acknowledge(alertId, actor));
            assertThrows(NotAuthorizedException.class, () -> service.startWork(alertId, actor));
        }
        verify(alertRepository, never()).save(any());
    }

    /** The reporter hears each move, with the authority by name. */
    @Test
    void tellsTheReporterWhatTheAuthorityDid() {
        saves();
        User lozenets = official("LOZENETS");

        service.acknowledge(alertId, lozenets);
        service.startWork(alertId, lozenets);

        ArgumentCaptor<ReportProgressed> event = ArgumentCaptor.forClass(ReportProgressed.class);
        verify(events, times(2)).publishEvent(event.capture());
        assertEquals(List.of(AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS),
            event.getAllValues().stream().map(e -> e.alert().status()).toList());
        assertEquals("Район Лозенец", event.getAllValues().getFirst().authorityName());
    }

    /** A refused move is not news. */
    @Test
    void aRefusedMoveTellsNobody() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));

        assertThrows(NotAuthorizedException.class, () -> service.acknowledge(alertId, official("TRIADITSA")));
        verifyNoInteractions(events);
    }

    @Test
    void recordsWhoClosedTheReport() {
        saves();
        assertEquals(ClosedBy.AUTHORITY, service.resolve(alertId, official("LOZENETS")).closedBy());
        assertEquals(ClosedBy.REPORTER, service.resolve(alertId, user(ownerId, Role.USER)).closedBy());
        assertEquals(ClosedBy.MODERATOR, service.resolve(alertId, user(UUID.randomUUID(), Role.MODERATOR)).closedBy());
    }

    @Test
    void anOfficialListsOnlyTheirOwnDistrict() {
        when(alertRepository.findAssignedTo(eq("LOZENETS"), any(), anyInt())).thenReturn(List.of(active()));

        List<Alert> listed = service.listForOwnAuthority(official("LOZENETS"), Set.of());

        assertEquals(1, listed.size());
        verify(alertRepository).findAssignedTo("LOZENETS", AlertLifecycleService.OPEN, AlertLifecycleService.LIMIT);
    }

    @Test
    void someoneWithoutADistrictHasNoDistrictList() {
        assertThrows(NotAuthorizedException.class,
                () -> service.listForOwnAuthority(user(UUID.randomUUID(), Role.MODERATOR), Set.of()));
        verifyNoInteractions(alertRepository);
    }
}

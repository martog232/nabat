package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.application.port.out.ForwardStore;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.ReportForward;
import org.example.nabat.incident.domain.ReportForwarded;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForwardReportServiceTest {

    @Mock private AlertRepository alerts;
    @Mock private ForwardStore forwards;
    @Mock private CommentStore comments;
    @Mock private ResponsibleAuthorityPort authorities;
    @Mock private AlertNotificationPort broadcast;
    @Mock private ApplicationEventPublisher events;

    private ForwardReportService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ForwardReportService(alerts, forwards, comments, authorities, broadcast, events);
        lenient().when(alerts.findById(alertId)).thenReturn(Optional.of(report()));
        lenient().when(authorities.nameOf("CGM")).thenReturn(Optional.of("Център за градска мобилност"));
        lenient().when(alerts.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(forwards.forwardsOf(alertId.value())).thenReturn(List.of());
    }

    @Test
    void theAuthorityHandsItOnSaysWhyAndItIsMailedThere() {
        User lozenets = user(Role.OFFICIAL, "LOZENETS");

        Alert moved = service.forward(alertId, lozenets, "CGM", "  Спирката е на градския транспорт ");

        assertThat(moved.authorityCode()).isEqualTo("CGM");
        assertThat(moved.status()).isEqualTo(AlertStatus.SUBMITTED);

        ArgumentCaptor<ReportForward> forward = ArgumentCaptor.forClass(ReportForward.class);
        verify(forwards).insert(forward.capture());
        assertThat(forward.getValue().sequence()).isEqualTo(1);
        assertThat(forward.getValue().fromAuthority()).isEqualTo("LOZENETS");
        assertThat(forward.getValue().toAuthority()).isEqualTo("CGM");
        assertThat(forward.getValue().reason()).isEqualTo("Спирката е на градския транспорт");
        assertThat(forward.getValue().byModerator()).isFalse();

        ArgumentCaptor<Comment> comment = ArgumentCaptor.forClass(Comment.class);
        verify(comments).insert(comment.capture());
        assertThat(comment.getValue().body())
            .isEqualTo("Препратен към Център за градска мобилност: Спирката е на градския транспорт");
        assertThat(comment.getValue().byAuthority()).isTrue();

        ArgumentCaptor<ReportForwarded> event = ArgumentCaptor.forClass(ReportForwarded.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().alert().authorityCode()).isEqualTo("CGM");
        assertThat(event.getValue().toAuthorityName()).isEqualTo("Център за градска мобилност");
    }

    /** A moderator corrects the routing; the record says it was not the authority's own word. */
    @Test
    void aModeratorMayForwardAndIsRecordedAsOne() {
        service.forward(alertId, user(Role.MODERATOR, null), "CGM", "Грешна категория");

        ArgumentCaptor<ReportForward> forward = ArgumentCaptor.forClass(ReportForward.class);
        verify(forwards).insert(forward.capture());
        assertThat(forward.getValue().byModerator()).isTrue();
    }

    /** The reporter chose the category, which chose the authority; another authority's official is a citizen here. */
    @Test
    void nobodyElseMayForwardIt() {
        User reporter = user(reporterId, Role.USER, null);
        User otherDistrict = user(Role.OFFICIAL, "SREDETS");

        assertThrows(NotAuthorizedException.class, () -> service.forward(alertId, reporter, "CGM", "Не е на района"));
        assertThrows(NotAuthorizedException.class, () -> service.forward(alertId, otherDistrict, "CGM", "Не е наш"));
        verify(alerts, never()).save(any());
        verifyNoInteractions(forwards, comments, events);
    }

    @Test
    void aForwardSaysWhy() {
        User lozenets = user(Role.OFFICIAL, "LOZENETS");

        assertThrows(IllegalArgumentException.class, () -> service.forward(alertId, lozenets, "CGM", "   "));
        assertThrows(IllegalArgumentException.class,
            () -> service.forward(alertId, lozenets, "CGM", "x".repeat(ReportForward.MAX_REASON_LENGTH + 1)));
        verifyNoInteractions(events);
    }

    @Test
    void refusesAnAuthorityThatDoesNotExist() {
        when(authorities.nameOf("NOWHERE")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> service.forward(alertId, user(Role.OFFICIAL, "LOZENETS"), "NOWHERE", "Не е наш"));
        verify(alerts, never()).save(any());
    }

    /** Two authorities that each think it is the other's could pass it between them for ever. */
    @Test
    void afterThreeForwardsOnlyAModeratorDecides() {
        List<ReportForward> three = Collections.nCopies(3, new ReportForward(UUID.randomUUID(), alertId, 1,
            "SREDETS", "LOZENETS", UUID.randomUUID(), false, "Не е наш", Instant.now()));
        when(forwards.forwardsOf(alertId.value())).thenReturn(three);

        assertThrows(NotAuthorizedException.class,
            () -> service.forward(alertId, user(Role.OFFICIAL, "LOZENETS"), "CGM", "Не е наш"));

        Alert moved = service.forward(alertId, user(Role.MODERATOR, null), "CGM", "Решено от модератор");
        assertThat(moved.authorityCode()).isEqualTo("CGM");
        ArgumentCaptor<ReportForward> forward = ArgumentCaptor.forClass(ReportForward.class);
        verify(forwards).insert(forward.capture());
        assertThat(forward.getValue().sequence()).isEqualTo(4);
    }

    private Alert report() {
        return new Alert(alertId, "Счупен заслон", "Спирка пред номер 5", 1007, Location.of(42.67, 23.32),
            Instant.now(), AlertStatus.ACKNOWLEDGED, reporterId, 0, 0, 0, 0, null, null, "LOZENETS", null);
    }

    private static User user(Role role, String authority) {
        return user(UUID.randomUUID(), role, authority);
    }

    private static User user(UUID id, Role role, String authority) {
        return new User(UserId.of(id), "u@example.com", "p", "Служител", role, true, true,
            Instant.now(), Instant.now(), 0, authority);
    }
}

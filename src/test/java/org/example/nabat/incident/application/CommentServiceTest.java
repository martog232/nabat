package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.CommentAdded;
import org.example.nabat.incident.domain.CommentNotFoundException;
import org.example.nabat.incident.domain.CommentRemoval;
import org.example.nabat.incident.domain.CommentsLockedException;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private AlertRepository alerts;
    @Mock private CommentStore store;
    @Mock private ApplicationEventPublisher events;

    private CommentService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommentService(alerts, store, events);
    }

    @Test
    void addsACommentAndTellsTheReporterAndEveryoneElseInTheThread() {
        UUID earlier = UUID.randomUUID();
        User me = user(Role.USER, null);
        openReport();
        when(store.authorsOn(alertId.value())).thenReturn(Set.of(earlier, me.id().value()));

        Comment added = service.add(alertId, me, "  Още е там.  ");

        assertThat(added.body()).isEqualTo("Още е там.");
        assertThat(added.authorName()).isEqualTo("Гражданин");
        assertThat(added.byDistrict()).isFalse();
        assertThat(added.byReporter()).isFalse();
        verify(store).insert(added);
        ArgumentCaptor<CommentAdded> event = ArgumentCaptor.forClass(CommentAdded.class);
        verify(events).publishEvent(event.capture());
        // Never the author, even though they have written here before.
        assertThat(event.getValue().recipients()).containsExactlyInAnyOrder(reporterId, earlier);
    }

    @Test
    void marksTheDistrictsOwnComment() {
        openReport();

        Comment added = service.add(alertId, user(Role.DISTRICT_OFFICIAL, "LOZENETS"), "Насочено към поддръжката.");

        assertThat(added.byDistrict()).isTrue();
    }

    @Test
    void anotherDistrictsOfficialWritesLikeAnyoneElse() {
        openReport();

        Comment added = service.add(alertId, user(Role.DISTRICT_OFFICIAL, "MLADOST"), "И при нас има такава.");

        assertThat(added.byDistrict()).isFalse();
    }

    /** The reporter writing alone in their own thread has nobody to tell. */
    @Test
    void tellsNobodyWhenThereIsNobodyElse() {
        openReport();

        Comment added = service.add(alertId, user(reporterId, Role.USER, null), "Снимка от днес.");

        assertThat(added.byReporter()).isTrue();
        verify(events, never()).publishEvent(any());
    }

    @ParameterizedTest
    @EnumSource(value = AlertStatus.class, names = {"RESOLVED", "REJECTED"})
    void aClosedReportTakesNoComments(AlertStatus closed) {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report()));
        when(store.holdReport(alertId.value())).thenReturn(closed);

        assertThatThrownBy(() -> service.add(alertId, user(Role.USER, null), "Не е оправено."))
            .isInstanceOf(CommentsLockedException.class);
        verify(store, never()).insert(any());
    }

    /** The status that counts is the one read under the lock, not the one loaded before it. */
    @Test
    void theLockedStatusDecides() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report()));
        when(store.holdReport(alertId.value())).thenReturn(AlertStatus.RESOLVED);

        assertThatThrownBy(() -> service.add(alertId, user(Role.USER, null), "Току-що."))
            .isInstanceOf(CommentsLockedException.class);
    }

    @Test
    void refusesABlankOrOverlongComment() {
        User me = user(Role.USER, null);

        assertThatThrownBy(() -> service.add(alertId, me, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(alertId, me, "а".repeat(Comment.MAX_LENGTH + 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAuthorTakesTheirOwnCommentDownWhileTheReportIsOpen() {
        User me = user(Role.USER, null);
        Comment mine = comment(me.id().value());
        when(store.find(alertId.value(), mine.id())).thenReturn(Optional.of(mine));
        when(store.holdReport(alertId.value())).thenReturn(AlertStatus.IN_PROGRESS);

        Comment removed = service.remove(alertId, mine.id(), me);

        assertThat(removed.removedBy()).isEqualTo(CommentRemoval.AUTHOR);
        assertThat(removed.body()).isNull();
        verify(store).markRemoved(eq(mine.id()), any(), eq(CommentRemoval.AUTHOR));
    }

    /** A closed thread is a record: its participants cannot edit it after the fact. */
    @Test
    void anAuthorCannotTakeACommentBackOnceTheReportIsClosed() {
        User me = user(Role.USER, null);
        Comment mine = comment(me.id().value());
        when(store.find(alertId.value(), mine.id())).thenReturn(Optional.of(mine));
        when(store.holdReport(alertId.value())).thenReturn(AlertStatus.RESOLVED);

        assertThatThrownBy(() -> service.remove(alertId, mine.id(), me)).isInstanceOf(CommentsLockedException.class);
        verify(store, never()).markRemoved(any(), any(), any());
    }

    /** Frozen for participants, not for abuse. */
    @Test
    void aModeratorTakesACommentDownEvenInAClosedThread() {
        Comment someones = comment(UUID.randomUUID());
        when(store.find(alertId.value(), someones.id())).thenReturn(Optional.of(someones));

        Comment removed = service.remove(alertId, someones.id(), user(Role.MODERATOR, null));

        assertThat(removed.removedBy()).isEqualTo(CommentRemoval.MODERATOR);
        verify(store, never()).holdReport(any());
    }

    @Test
    void nobodyElseTakesAnotherPersonsCommentDown() {
        Comment someones = comment(UUID.randomUUID());
        when(store.find(alertId.value(), someones.id())).thenReturn(Optional.of(someones));

        assertThatThrownBy(() -> service.remove(alertId, someones.id(), user(Role.DISTRICT_OFFICIAL, "LOZENETS")))
            .isInstanceOf(NotAuthorizedException.class);
    }

    /** A comment id from another report is not found here, rather than removable through this one. */
    @Test
    void aCommentIsOnlyFoundOnItsOwnReport() {
        UUID elsewhere = UUID.randomUUID();
        when(store.find(alertId.value(), elsewhere)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(alertId, elsewhere, user(Role.MODERATOR, null)))
            .isInstanceOf(CommentNotFoundException.class);
    }

    private void openReport() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report()));
        when(store.holdReport(alertId.value())).thenReturn(AlertStatus.SENT);
    }

    private Alert report() {
        return new Alert(alertId, "Дупка", "Дълбока", 1007, Location.of(42.67, 23.32), Instant.now(), AlertStatus.SENT,
            reporterId, 0, 0, 0, 0, null, null, "LOZENETS", null);
    }

    private Comment comment(UUID author) {
        return new Comment(UUID.randomUUID(), alertId, author, "Някой", false, false, "Текст", Instant.now(), null, null);
    }

    private static User user(Role role, String district) {
        return user(UUID.randomUUID(), role, district);
    }

    private static User user(UUID id, Role role, String district) {
        return new User(UserId.of(id), "u@example.com", "p", "Гражданин", role, true, true,
            Instant.now(), Instant.now(), 0, district);
    }
}

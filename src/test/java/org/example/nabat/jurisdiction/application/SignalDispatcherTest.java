package org.example.nabat.jurisdiction.application;

import org.example.nabat.identity.application.port.in.SignInLinkUseCase;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.application.port.in.RecordDeliveryUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.ReportForward;
import org.example.nabat.incident.domain.ReportForwarded;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalDispatchRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.example.nabat.jurisdiction.domain.Authority;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.jurisdiction.domain.Institution;
import org.example.nabat.jurisdiction.domain.SignalDispatch;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalDispatcherTest {

    private static final District LOZENETS =
        new District("LOZENETS", "Район Лозенец", "lozenets@rayon.nabat.test");

    @Mock private DistrictRepository districts;
    @Mock private ListAuthoritiesUseCase authorities;
    @Mock private SignalDispatchRepository dispatches;
    @Mock private SignalMailer mailer;
    @Mock private UserRepository users;
    @Mock private RecordDeliveryUseCase recordDelivery;
    @Mock private ListReportCategoriesUseCase categories;
    @Mock private GetAlertByIdUseCase reports;
    @Mock private SignInLinkUseCase signInLinks;

    private SignalDispatcher dispatcher;
    private User reporter;
    private Alert alert;

    @BeforeEach
    void setUp() {
        dispatcher = new SignalDispatcher(districts, authorities, dispatches, mailer, users, recordDelivery, categories, reports, signInLinks);
        reporter = User.create("ivan@example.com", "hash", "Иван Петров");
        alert = Alert.create("Дупка", "Пред номер 12", 1007,
            Location.of(42.6750, 23.3200), reporter.id().value(), null, "LOZENETS", "LOZENETS");
    }

    private void knownDistrictAndReporter() {
        when(districts.findByCode("LOZENETS")).thenReturn(Optional.of(LOZENETS));
        when(authorities.findAuthority("LOZENETS")).thenReturn(Optional.of(Authority.of(LOZENETS)));
        when(users.findById(reporter.id())).thenReturn(Optional.of(reporter));
        when(reports.getById(alert.id())).thenReturn(alert);
    }

    @Test
    void sendsTheReportToItsDistrictInTheReportersName() {
        knownDistrictAndReporter();
        when(signInLinks.issueFor("LOZENETS")).thenReturn(Optional.of("link-secret"));

        dispatcher.on(new AlertCreated(alert));

        ArgumentCaptor<SignalMail> mail = ArgumentCaptor.forClass(SignalMail.class);
        verify(mailer).send(mail.capture());
        assertEquals("lozenets@rayon.nabat.test", mail.getValue().to());
        assertEquals("Район Лозенец", mail.getValue().recipientName());
        assertEquals("Район Лозенец", mail.getValue().districtName());
        assertEquals("Иван Петров", mail.getValue().reporterName());
        assertEquals("link-secret", mail.getValue().signInKey());
        assertEquals("ivan@example.com", mail.getValue().reporterEmail());
        assertEquals(alert.id().value(), mail.getValue().alertId());
    }

    /** Recorded only once the mail has left — a record before it would claim a delivery that may fail. */
    @Test
    void recordsTheDispatchAndTheStatusAfterSending() {
        knownDistrictAndReporter();

        dispatcher.on(new AlertCreated(alert));

        InOrder order = inOrder(mailer, dispatches, recordDelivery);
        order.verify(mailer).send(any());
        ArgumentCaptor<SignalDispatch> dispatch = ArgumentCaptor.forClass(SignalDispatch.class);
        order.verify(dispatches).save(dispatch.capture());
        order.verify(recordDelivery).recordDelivered(alert.id(), "LOZENETS");
        assertEquals("LOZENETS", dispatch.getValue().districtCode());
        assertEquals("LOZENETS", dispatch.getValue().authorityCode());
        assertEquals("lozenets@rayon.nabat.test", dispatch.getValue().recipient());
    }

    /**
     * A category an institution takes goes to the institution, which is told the district so it
     * knows where; the dispatch keeps both.
     */
    @Test
    void sendsAReportToTheInstitutionItsCategoryBelongsTo() {
        Institution cgm = new Institution("CGM", "Център за градска мобилност", "cgm@institution.nabat.test");
        Alert busStop = Alert.create("Счупен заслон", "Спирка Лозенец", 1024,
            Location.of(42.6750, 23.3200), reporter.id().value(), null, "LOZENETS", "CGM");
        when(districts.findByCode("LOZENETS")).thenReturn(Optional.of(LOZENETS));
        when(authorities.findAuthority("CGM")).thenReturn(Optional.of(Authority.of(cgm)));
        when(users.findById(reporter.id())).thenReturn(Optional.of(reporter));
        when(reports.getById(busStop.id())).thenReturn(busStop);

        dispatcher.on(new AlertCreated(busStop));

        ArgumentCaptor<SignalMail> mail = ArgumentCaptor.forClass(SignalMail.class);
        verify(mailer).send(mail.capture());
        assertEquals("cgm@institution.nabat.test", mail.getValue().to());
        assertEquals("Център за градска мобилност", mail.getValue().recipientName());
        assertEquals("Район Лозенец", mail.getValue().districtName());
        ArgumentCaptor<SignalDispatch> dispatch = ArgumentCaptor.forClass(SignalDispatch.class);
        verify(dispatches).save(dispatch.capture());
        assertEquals("LOZENETS", dispatch.getValue().districtCode());
        assertEquals("CGM", dispatch.getValue().authorityCode());
        assertEquals("cgm@institution.nabat.test", dispatch.getValue().recipient());
    }

    /**
     * The failure has to reach the listener infrastructure: that is what keeps the
     * publication outstanding and the report due to be sent again.
     */
    @Test
    void letsAFailedSendThrowAndRecordsNothing() {
        knownDistrictAndReporter();
        doThrow(new MailSendException("SMTP down")).when(mailer).send(any());

        assertThrows(MailSendException.class, () -> dispatcher.on(new AlertCreated(alert)));

        verify(dispatches, never()).save(any());
        verifyNoInteractions(recordDelivery);
    }

    /** A replay after a crash between the send and the completion must not mail the district twice. */
    @Test
    void doesNotSendAgainWhatWasAlreadySent() {
        when(dispatches.exists(alert.id().value(), 0)).thenReturn(true);

        dispatcher.on(new AlertCreated(alert));

        verifyNoInteractions(mailer);
        verify(dispatches, never()).save(any());
        // The status may be what did not get committed last time.
        verify(recordDelivery).recordDelivered(alert.id(), "LOZENETS");
    }
    /**
     * A forward is delivered like a new report, to the new authority, with who sent it on and
     * why at the top; recorded as its own delivery, so it is deduplicated apart from the first.
     */
    @Test
    void mailsAForwardedReportToTheAuthorityItWentTo() {
        Institution cgm = new Institution("CGM", "Център за градска мобилност", "cgm@institution.nabat.test");
        Alert moved = alert.forwardTo("CGM");
        ReportForward forward = new ReportForward(UUID.randomUUID(), alert.id(), 1, "LOZENETS", "CGM",
            UUID.randomUUID(), false, "Спирката е на градския транспорт", Instant.now());
        when(districts.findByCode("LOZENETS")).thenReturn(Optional.of(LOZENETS));
        when(authorities.findAuthority("LOZENETS")).thenReturn(Optional.of(Authority.of(LOZENETS)));
        when(authorities.findAuthority("CGM")).thenReturn(Optional.of(Authority.of(cgm)));
        when(users.findById(reporter.id())).thenReturn(Optional.of(reporter));
        when(reports.getById(alert.id())).thenReturn(moved);

        dispatcher.on(new ReportForwarded(moved, forward, "Център за градска мобилност"));

        ArgumentCaptor<SignalMail> mail = ArgumentCaptor.forClass(SignalMail.class);
        verify(mailer).send(mail.capture());
        assertEquals("cgm@institution.nabat.test", mail.getValue().to());
        assertEquals("Препратен към вас от Район Лозенец: Спирката е на градския транспорт", mail.getValue().note());
        ArgumentCaptor<SignalDispatch> dispatch = ArgumentCaptor.forClass(SignalDispatch.class);
        verify(dispatches).save(dispatch.capture());
        assertEquals(1, dispatch.getValue().sequence());
        assertEquals("CGM", dispatch.getValue().authorityCode());
        verify(recordDelivery).recordDelivered(alert.id(), "CGM");
    }

    /**
     * The creation mail had not left when the report was forwarded. Sending it now would tell
     * the old authority about a report that is no longer theirs, and mark it SENT for them.
     */
    @Test
    void doesNotMailAnAuthorityTheReportHasSinceBeenForwardedFrom() {
        when(reports.getById(alert.id())).thenReturn(alert.forwardTo("CGM"));

        dispatcher.on(new AlertCreated(alert));

        verifyNoInteractions(mailer, recordDelivery);
        verify(dispatches, never()).save(any());
    }
}

package org.example.nabat.jurisdiction.application;

import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.application.port.in.RecordDeliveryUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalDispatchRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.example.nabat.jurisdiction.domain.District;
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

import java.util.Optional;

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
    @Mock private SignalDispatchRepository dispatches;
    @Mock private SignalMailer mailer;
    @Mock private UserRepository users;
    @Mock private RecordDeliveryUseCase recordDelivery;
    @Mock private ListReportCategoriesUseCase categories;

    private SignalDispatcher dispatcher;
    private User reporter;
    private Alert alert;

    @BeforeEach
    void setUp() {
        dispatcher = new SignalDispatcher(districts, dispatches, mailer, users, recordDelivery, categories);
        reporter = User.create("ivan@example.com", "hash", "Иван Петров");
        alert = Alert.create("Дупка", "Пред номер 12", 1007,
            Location.of(42.6750, 23.3200), reporter.id().value(), null, "LOZENETS", "LOZENETS");
    }

    private void knownDistrictAndReporter() {
        when(districts.findByCode("LOZENETS")).thenReturn(Optional.of(LOZENETS));
        when(users.findById(reporter.id())).thenReturn(Optional.of(reporter));
    }

    @Test
    void sendsTheReportToItsDistrictInTheReportersName() {
        knownDistrictAndReporter();

        dispatcher.on(new AlertCreated(alert));

        ArgumentCaptor<SignalMail> mail = ArgumentCaptor.forClass(SignalMail.class);
        verify(mailer).send(mail.capture());
        assertEquals("lozenets@rayon.nabat.test", mail.getValue().to());
        assertEquals("Район Лозенец", mail.getValue().districtName());
        assertEquals("Иван Петров", mail.getValue().reporterName());
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
        order.verify(recordDelivery).recordDelivered(alert.id());
        assertEquals("LOZENETS", dispatch.getValue().districtCode());
        assertEquals("lozenets@rayon.nabat.test", dispatch.getValue().recipient());
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
        when(dispatches.existsForAlert(alert.id().value())).thenReturn(true);

        dispatcher.on(new AlertCreated(alert));

        verifyNoInteractions(mailer);
        verify(dispatches, never()).save(any());
        // The status may be what did not get committed last time.
        verify(recordDelivery).recordDelivered(alert.id());
    }
}

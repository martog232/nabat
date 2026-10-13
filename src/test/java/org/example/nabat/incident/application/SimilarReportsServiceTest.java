package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase.SimilarQuery;
import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase.SimilarReports;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CallSofiaReportsPort;
import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.incident.application.port.out.ReportCategoryRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ReportCategory;
import org.example.nabat.incident.domain.UnknownCategoryException;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarReportsServiceTest {

    private static final Location HERE = Location.of(42.6700, 23.3200);

    @Mock private AlertRepository alerts;
    @Mock private ReportCategoryRepository categories;
    @Mock private CallSofiaReportsPort callSofia;

    private SimilarReportsService service;

    private static final ReportCategory ROADS = new ReportCategory(1006, null, "Пътища и шахти", null, "#1E3A8A", 2);
    private static final ReportCategory ROADWAY = new ReportCategory(1007, 1006, "Улично платно", "дупки", null, 1);
    private static final ReportCategory PAVEMENT = new ReportCategory(1008, 1006, "Тротоар", "плочки", null, 2);
    private static final ReportCategory LIGHTS = new ReportCategory(1028, 1027, "Улично осветление", "лампи", null, 1);

    @BeforeEach
    void setUp() {
        service = new SimilarReportsService(alerts, categories, callSofia);
        lenient().when(categories.findAll()).thenReturn(List.of(ROADS, ROADWAY, PAVEMENT, LIGHTS));
        lenient().when(categories.findByCode(1007)).thenReturn(Optional.of(ROADWAY));
        lenient().when(categories.findByCode(1006)).thenReturn(Optional.of(ROADS));
    }

    /** A pothole filed as „Тротоар“ is still the pothole someone else filed as „Улично платно“. */
    @SuppressWarnings("unchecked")
    @Test
    void searchesTheWholeGroupOfTheChosenCategory() {
        when(alerts.findOpenNear(any(), anyDouble(), any(), anyInt())).thenReturn(List.of());
        when(callSofia.findOpenNear(any(), anyDouble(), any(), anyInt())).thenReturn(List.of());

        service.findSimilar(new SimilarQuery(HERE.latitude(), HERE.longitude(), 1007));

        ArgumentCaptor<Collection<Integer>> codes = ArgumentCaptor.forClass(Collection.class);
        verify(alerts).findOpenNear(eq(HERE), eq(100.0), codes.capture(), eq(5));
        assertThat(codes.getValue()).containsExactlyInAnyOrder(1007, 1008);
        verify(callSofia).findOpenNear(eq(HERE), eq(100.0), eq(codes.getValue()), eq(5));
    }

    @Test
    void saysHowFarEachOneIs() {
        Location fiftyMetresNorth = Location.of(42.67045, 23.3200);
        when(alerts.findOpenNear(any(), anyDouble(), any(), anyInt())).thenReturn(List.of(report(fiftyMetresNorth)));
        when(callSofia.findOpenNear(any(), anyDouble(), any(), anyInt())).thenReturn(List.of(
            new OpenCallSofiaReport(788076L, "СОА26-КЦ01-1", "Дупка", 1008, Instant.now(), HERE)));

        SimilarReports found = service.findSimilar(new SimilarQuery(HERE.latitude(), HERE.longitude(), 1007));

        assertThat(found.radiusMeters()).isEqualTo(100);
        assertThat(found.reports()).singleElement().satisfies(near -> assertThat(near.meters()).isBetween(48, 52));
        assertThat(found.callSofia()).singleElement().satisfies(near -> assertThat(near.meters()).isZero());
    }

    @Test
    void refusesAGroupCode() {
        assertThatThrownBy(() -> service.findSimilar(new SimilarQuery(HERE.latitude(), HERE.longitude(), 1006)))
            .isInstanceOf(UnknownCategoryException.class);
        verifyNoInteractions(alerts, callSofia);
    }

    private static Alert report(Location where) {
        return new Alert(AlertId.generate(), "Дупка", "Дълбока", 1007, where, Instant.now(), AlertStatus.SENT,
            UUID.randomUUID(), 0, 0, 2, 0, null, null, "LOZENETS", null);
    }
}

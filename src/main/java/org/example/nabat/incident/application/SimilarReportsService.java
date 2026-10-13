package org.example.nabat.incident.application;

import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CallSofiaReportsPort;
import org.example.nabat.incident.application.port.out.ReportCategoryRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.ReportCategory;
import org.example.nabat.incident.domain.UnknownCategoryException;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.Location;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds what might be the same problem, so a second report of it becomes a confirmation
 * of the first instead.
 *
 * <p>By group, not subcategory: the same pothole is filed as „Улично платно“ by one person
 * and „Тротоар“ by the next, and a check that missed that would miss the commonest case.
 *
 * <p>100 m: a phone's fix is good to a few tens of metres outdoors, and two reports of one
 * problem are usually made from different spots on the same street. Wide enough to catch
 * that, narrow enough that a different block is not offered as the same thing.
 */
@UseCase
public class SimilarReportsService implements FindSimilarReportsUseCase {

    static final int RADIUS_METERS = 100;
    static final int LIMIT = 5;

    private final AlertRepository alerts;
    private final ReportCategoryRepository categories;
    private final CallSofiaReportsPort callSofia;

    public SimilarReportsService(AlertRepository alerts, ReportCategoryRepository categories, CallSofiaReportsPort callSofia) {
        this.alerts = alerts;
        this.categories = categories;
        this.callSofia = callSofia;
    }

    @Override
    @Transactional(readOnly = true)
    public SimilarReports findSimilar(SimilarQuery query) {
        ReportCategory category = categories.findByCode(query.categoryCode())
            .filter(found -> !found.isGroup())
            .orElseThrow(() -> new UnknownCategoryException(query.categoryCode()));
        Set<Integer> sameGroup = categories.findAll().stream()
            .filter(other -> category.groupCode().equals(other.groupCode()))
            .map(ReportCategory::code)
            .collect(Collectors.toSet());
        Location center = Location.of(query.latitude(), query.longitude());

        List<Near<Alert>> reports = alerts
            .findOpenNear(center, RADIUS_METERS, sameGroup, LIMIT).stream()
            .map(alert -> new Near<>(alert, meters(center, alert.location())))
            .sorted(Comparator.comparingInt(Near::meters))
            .toList();
        List<Near<OpenCallSofiaReport>> elsewhere = callSofia
            .findOpenNear(center, RADIUS_METERS, sameGroup, LIMIT).stream()
            .map(report -> new Near<>(report, meters(center, report.location())))
            .sorted(Comparator.comparingInt(Near::meters))
            .toList();

        return new SimilarReports(RADIUS_METERS, reports, elsewhere);
    }

    private static int meters(Location from, Location to) {
        return (int) Math.round(from.distanceTo(to) * 1000);
    }
}

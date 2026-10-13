package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.incident.domain.Alert;

import java.util.List;

/**
 * "Is this already reported?" — asked before a report is filed, with the place and the
 * category the person has chosen.
 */
public interface FindSimilarReportsUseCase {

    /**
     * Open reports near the point in the same category group, in nabat and in Call Sofia,
     * nearest first.
     *
     * @throws org.example.nabat.incident.domain.UnknownCategoryException if the category is
     *         not a subcategory
     */
    SimilarReports findSimilar(SimilarQuery query);

    record SimilarQuery(double latitude, double longitude, int categoryCode) {
    }

    /** @param meters distance from the point asked about, rounded */
    record Near<T>(T report, int meters) {
    }

    record SimilarReports(int radiusMeters, List<Near<Alert>> reports, List<Near<OpenCallSofiaReport>> callSofia) {
    }
}

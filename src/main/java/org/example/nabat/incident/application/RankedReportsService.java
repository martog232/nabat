package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.ListRankedReportsUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.AlertRepository.RankedSearch;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RankedReportsService implements ListRankedReportsUseCase {

    private final AlertRepository alertRepository;

    public RankedReportsService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Not cached. The {@code HOT} order moves with the clock, so any cached page is wrong a
     * little more every minute, and the list is read far less often than the map.
     */
    @Override
    @Transactional(readOnly = true)
    public RankedPage listRanked(RankedQuery query) {
        RankedSearch search = new RankedSearch(
            query.districtCode(), query.statuses(), query.ranking(),
            query.page() * query.size(), query.size(), query.overdue());
        return new RankedPage(alertRepository.findRanked(search), alertRepository.countRanked(search));
    }
}

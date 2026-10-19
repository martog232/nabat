package org.example.nabat.jurisdiction.adapter.out.incident;

import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.jurisdiction.application.port.in.AssignAuthorityUseCase;
import org.springframework.stereotype.Component;

/** Answers {@code incident}'s question — who answers for this report — from the routing here. */
@Component
public class ResponsibleAuthorityAdapter implements ResponsibleAuthorityPort {

    private final AssignAuthorityUseCase assign;

    public ResponsibleAuthorityAdapter(AssignAuthorityUseCase assign) {
        this.assign = assign;
    }

    @Override
    public String authorityFor(String districtCode, int categoryCode) {
        return assign.responsibleFor(districtCode, categoryCode);
    }
}

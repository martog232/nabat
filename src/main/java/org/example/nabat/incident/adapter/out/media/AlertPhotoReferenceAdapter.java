package org.example.nabat.incident.adapter.out.media;

import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.media.application.port.out.PhotoReferencePort;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Tells {@code media} which of its stored photos alerts still point at.
 *
 * <p>Implemented here rather than in {@code media} because this module owns
 * {@code alerts.photo_url}. Same direction as {@code AlertAudienceAdapter}: the port is
 * declared by the module that needs the answer.
 *
 * <p>Alerts store a URL ({@code /api/v1/uploads/<name>}) while the sweeper deals in
 * storage names, so the match is on the URL's suffix. Comparing whole strings would need
 * this adapter to know how {@code media} builds a URL, which is exactly the coupling the
 * port exists to avoid.
 */
@Component
public class AlertPhotoReferenceAdapter implements PhotoReferencePort {

    private final AlertJpaRepository alertRepository;

    public AlertPhotoReferenceAdapter(AlertJpaRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public Set<String> referencedAmong(Set<String> filenames) {
        if (filenames.isEmpty()) {
            return Set.of();
        }
        // Any exception propagates on purpose. The caller deletes what is missing from
        // this set, so returning an empty set on failure would wipe the volume.
        return alertRepository.findReferencedPhotoFilenames(filenames);
    }
}

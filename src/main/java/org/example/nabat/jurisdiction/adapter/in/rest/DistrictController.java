package org.example.nabat.jurisdiction.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.example.nabat.jurisdiction.application.port.in.ListDistrictsUseCase;
import org.example.nabat.jurisdiction.domain.District;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/districts")
public class DistrictController {

    private final ListDistrictsUseCase listDistricts;

    public DistrictController(ListDistrictsUseCase listDistricts) {
        this.listDistricts = listDistricts;
    }

    /**
     * The districts reports are routed to, so a client can turn a report's
     * {@code districtCode} into a name. Cacheable for a day: the list changes with a
     * migration, not with use. Public, like the ranked list it names the districts for.
     */
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "The districts of Sofia that reports are routed to")
    public ResponseEntity<List<DistrictResponse>> list() {
        List<DistrictResponse> body = listDistricts.listDistricts().stream()
            .map(DistrictResponse::from)
            .toList();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .body(body);
    }

    @Schema(description = "A district of Sofia")
    public record DistrictResponse(
        @Schema(description = "Stable code, as carried by a report's districtCode", example = "LOZENETS") String code,
        @Schema(description = "Name of the district", example = "Район Лозенец") String name,
        @Schema(description = "Estimated residents, from Yurukov's Call Sofia archive (2022) — for per-resident ratios, not a census", example = "53110") int population
    ) {
        static DistrictResponse from(District district) {
            return new DistrictResponse(district.code(), district.name(), district.population());
        }
    }
}

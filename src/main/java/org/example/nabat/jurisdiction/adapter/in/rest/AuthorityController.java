package org.example.nabat.jurisdiction.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.domain.Authority;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/authorities")
public class AuthorityController {

    private final ListAuthoritiesUseCase listAuthorities;

    public AuthorityController(ListAuthoritiesUseCase listAuthorities) {
        this.listAuthorities = listAuthorities;
    }

    /**
     * Who reports go to, so a client can turn a report's {@code authorityCode} into a name.
     * Public and cacheable for a day, like {@code /districts}: it changes with a migration.
     * The mailboxes are left out — the public has no use for them, and nabat is where an
     * authority is answered.
     */
    @GetMapping
    @SecurityRequirements
    @Operation(summary = "The districts and city-wide institutions that reports go to")
    public ResponseEntity<List<AuthorityResponse>> list() {
        List<AuthorityResponse> body = listAuthorities.listAuthorities().stream()
            .map(AuthorityResponse::from)
            .toList();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .body(body);
    }

    @Schema(description = "Someone a report can go to: a district administration, or a city-wide institution")
    public record AuthorityResponse(
        @Schema(description = "Stable code, as carried by a report's authorityCode", example = "CGM") String code,
        @Schema(description = "Name", example = "Център за градска мобилност") String name,
        @Schema(description = "A district administration, or an institution that takes some categories city-wide") Authority.Kind kind
    ) {
        static AuthorityResponse from(Authority authority) {
            return new AuthorityResponse(authority.code(), authority.name(), authority.kind());
        }
    }
}

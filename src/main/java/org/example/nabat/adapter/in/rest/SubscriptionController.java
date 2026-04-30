package org.example.nabat.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.in.SubscribeToAlertsUseCase;
import org.example.nabat.application.port.in.SubscribeToAlertsUseCase.SubscribeCommand;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserSubscription;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscribeToAlertsUseCase useCase;

    @GetMapping
    public List<SubscriptionResponse> listMine(@AuthenticationPrincipal User user) {
        return useCase.listMine(user.id()).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(
            @Valid @RequestBody SubscriptionRequest request,
            @AuthenticationPrincipal User user
    ) {
        UserSubscription saved = useCase.subscribe(new SubscribeCommand(
                user.id(),
                request.alertType(),
                request.latitude(),
                request.longitude(),
                request.radiusKm()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        useCase.unsubscribe(id, user.id());
        return ResponseEntity.noContent().build();
    }

    public record SubscriptionRequest(
            @NotNull AlertType alertType,
            @NotNull Double latitude,
            @NotNull Double longitude,
            @NotNull @Positive Double radiusKm
    ) {}

    public record SubscriptionResponse(
            UUID id,
            UUID userId,
            AlertType alertType,
            double latitude,
            double longitude,
            double radiusKm,
            boolean active,
            Instant createdAt
    ) {
        public static SubscriptionResponse from(UserSubscription s) {
            return new SubscriptionResponse(
                    s.id(),
                    s.userId().value(),
                    s.alertType(),
                    s.center().latitude(),
                    s.center().longitude(),
                    s.radiusKm(),
                    s.active(),
                    s.createdAt()
            );
        }
    }
}


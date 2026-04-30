package org.example.nabat.adapter.in.rest;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.in.GetNotificationUseCase;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final GetNotificationUseCase getNotificationUseCase;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal User user) {
        return getNotificationUseCase.getNotifications(user.id())
                .stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread")
    public List<NotificationResponse> unread(@AuthenticationPrincipal User user) {
        return getNotificationUseCase.getUnreadNotifications(user.id())
                .stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread/count")
    public Map<String, Integer> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", getNotificationUseCase.countUnreadNotifications(user.id()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        return NotificationResponse.from(
                getNotificationUseCase.markAsRead(NotificationId.of(id), user.id()));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAll(@AuthenticationPrincipal User user) {
        getNotificationUseCase.markAllAsRead(user.id());
        return ResponseEntity.noContent().build();
    }
}


package org.example.nabat.adapter.in.rest;

import org.example.nabat.application.port.in.IssueWebSocketTicketUseCase;
import org.example.nabat.domain.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/ws/tickets")
public class WebSocketTicketController {

    private final IssueWebSocketTicketUseCase issueWebSocketTicketUseCase;

    public WebSocketTicketController(IssueWebSocketTicketUseCase issueWebSocketTicketUseCase) {
        this.issueWebSocketTicketUseCase = issueWebSocketTicketUseCase;
    }

    @PostMapping
    public ResponseEntity<WebSocketTicketResponse> issueTicket(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IssueWebSocketTicketUseCase.IssuedWebSocketTicket issuedTicket = issueWebSocketTicketUseCase.issueTicket(
            new IssueWebSocketTicketUseCase.IssueWebSocketTicketCommand(user.id())
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(new WebSocketTicketResponse(issuedTicket.ticket(), issuedTicket.expiresAt()));
    }

    public record WebSocketTicketResponse(
        String ticket,
        Instant expiresAt
    ) {}
}


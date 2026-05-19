package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.UserId;

import java.time.Instant;

public interface IssueWebSocketTicketUseCase {
    IssuedWebSocketTicket issueTicket(IssueWebSocketTicketCommand command);

    record IssueWebSocketTicketCommand(UserId userId) {}

    record IssuedWebSocketTicket(
        String ticket,
        Instant expiresAt
    ) {}
}


package org.example.nabat.realtime.application.port.in;

import org.example.nabat.identity.domain.UserId;

import java.time.Instant;

public interface IssueWebSocketTicketUseCase {
    IssuedWebSocketTicket issueTicket(IssueWebSocketTicketCommand command);

    record IssueWebSocketTicketCommand(UserId userId) {}

    record IssuedWebSocketTicket(
        String ticket,
        Instant expiresAt
    ) {}
}


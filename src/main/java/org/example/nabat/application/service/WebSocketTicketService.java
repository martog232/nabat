package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.IssueWebSocketTicketUseCase;
import org.example.nabat.application.port.in.RedeemWebSocketTicketUseCase;
import org.example.nabat.application.port.out.WebSocketTicketRepository;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.WebSocketTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;

@UseCase
public class WebSocketTicketService implements IssueWebSocketTicketUseCase, RedeemWebSocketTicketUseCase {

    private final WebSocketTicketRepository webSocketTicketRepository;
    private final Duration ticketTtl;

    public WebSocketTicketService(
        WebSocketTicketRepository webSocketTicketRepository,
        @Value("${nabat.websocket.ticket-ttl:PT2M}") Duration ticketTtl
    ) {
        this.webSocketTicketRepository = webSocketTicketRepository;
        this.ticketTtl = ticketTtl;
    }

    @Override
    public IssuedWebSocketTicket issueTicket(IssueWebSocketTicketCommand command) {
        if (command == null || command.userId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        WebSocketTicket ticket = WebSocketTicket.issueFor(command.userId(), ticketTtl);
        webSocketTicketRepository.save(ticket);
        return new IssuedWebSocketTicket(ticket.value(), ticket.expiresAt());
    }

    @Override
    public UserId redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new BadCredentialsException("Missing WebSocket ticket");
        }

        WebSocketTicket issuedTicket = webSocketTicketRepository.consume(ticket.trim())
            .orElseThrow(() -> new BadCredentialsException("Invalid WebSocket ticket"));

        if (issuedTicket.isExpiredAt(Instant.now())) {
            throw new BadCredentialsException("Expired WebSocket ticket");
        }

        return issuedTicket.userId();
    }
}


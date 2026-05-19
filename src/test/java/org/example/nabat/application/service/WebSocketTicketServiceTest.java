package org.example.nabat.application.service;

import org.example.nabat.adapter.out.memory.InMemoryWebSocketTicketRepository;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.WebSocketTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketTicketServiceTest {

    private InMemoryWebSocketTicketRepository repository;
    private WebSocketTicketService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryWebSocketTicketRepository();
        service = new WebSocketTicketService(repository, Duration.ofMinutes(2));
    }

    @Test
    void issuesShortLivedTicketForAuthenticatedUser() {
        UserId userId = UserId.of(UUID.randomUUID());

        var issued = service.issueTicket(new org.example.nabat.application.port.in.IssueWebSocketTicketUseCase.IssueWebSocketTicketCommand(userId));

        assertNotNull(issued.ticket());
        assertTrue(issued.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void redeemsIssuedTicketExactlyOnce() {
        UserId userId = UserId.of(UUID.randomUUID());
        var issued = service.issueTicket(new org.example.nabat.application.port.in.IssueWebSocketTicketUseCase.IssueWebSocketTicketCommand(userId));

        assertEquals(userId, service.redeem(issued.ticket()));
        assertThrows(BadCredentialsException.class, () -> service.redeem(issued.ticket()));
    }

    @Test
    void rejectsExpiredTicket() {
        UserId userId = UserId.of(UUID.randomUUID());
        repository.save(new WebSocketTicket("expired-ticket", userId, Instant.now().minusSeconds(5)));

        assertThrows(BadCredentialsException.class, () -> service.redeem("expired-ticket"));
    }

    @Test
    void rejectsBlankTicket() {
        assertThrows(BadCredentialsException.class, () -> service.redeem("  "));
    }
}


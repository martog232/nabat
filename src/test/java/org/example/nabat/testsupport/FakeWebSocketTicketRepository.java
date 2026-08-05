package org.example.nabat.testsupport;

import org.example.nabat.realtime.application.port.out.WebSocketTicketRepository;
import org.example.nabat.realtime.domain.WebSocketTicket;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link WebSocketTicketRepository} for unit tests.
 *
 * <p>This is the old {@code InMemoryWebSocketTicketRepository}, moved out of production
 * code where it was a {@code @Component} and therefore the real implementation. That
 * could not work in the deployed topology — with two replicas, a ticket issued on one
 * pod was unredeemable on the other — so production now uses
 * {@code RedisWebSocketTicketRepository}. As a test double the in-memory behaviour is
 * exactly what is wanted, hence keeping it here.
 */
public class FakeWebSocketTicketRepository implements WebSocketTicketRepository {

    private final Map<String, WebSocketTicket> tickets = new ConcurrentHashMap<>();

    @Override
    public WebSocketTicket save(WebSocketTicket ticket) {
        tickets.put(ticket.value(), ticket);
        return ticket;
    }

    @Override
    public Optional<WebSocketTicket> consume(String ticketValue) {
        return Optional.ofNullable(tickets.remove(ticketValue));
    }
}

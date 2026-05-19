package org.example.nabat.adapter.out.memory;

import org.example.nabat.application.port.out.WebSocketTicketRepository;
import org.example.nabat.domain.model.WebSocketTicket;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryWebSocketTicketRepository implements WebSocketTicketRepository {

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


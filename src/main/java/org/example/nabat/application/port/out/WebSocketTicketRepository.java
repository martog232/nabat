package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.WebSocketTicket;

import java.util.Optional;

public interface WebSocketTicketRepository {
    WebSocketTicket save(WebSocketTicket ticket);
    Optional<WebSocketTicket> consume(String ticketValue);
}


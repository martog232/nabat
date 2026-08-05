package org.example.nabat.realtime.application.port.out;

import org.example.nabat.realtime.domain.WebSocketTicket;

import java.util.Optional;

public interface WebSocketTicketRepository {
    WebSocketTicket save(WebSocketTicket ticket);
    Optional<WebSocketTicket> consume(String ticketValue);
}


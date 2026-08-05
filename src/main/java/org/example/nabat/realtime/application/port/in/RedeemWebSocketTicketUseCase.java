package org.example.nabat.realtime.application.port.in;

import org.example.nabat.identity.domain.UserId;

public interface RedeemWebSocketTicketUseCase {
    UserId redeem(String ticket);
}


package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.UserId;

public interface RedeemWebSocketTicketUseCase {
    UserId redeem(String ticket);
}


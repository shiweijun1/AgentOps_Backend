package com.agentops.ticket.api;

import com.agentops.ticket.domain.TicketActorType;
import com.agentops.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketTransitionResponse(
        UUID id,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        TicketActorType actorType,
        String actorId,
        String reason,
        String commandId,
        Instant occurredAt
) {
}

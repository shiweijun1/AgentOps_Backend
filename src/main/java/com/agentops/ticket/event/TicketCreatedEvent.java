package com.agentops.ticket.event;

import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketCreatedEvent(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String traceId,
        String tenantId,
        UUID aggregateId,
        String ticketNo,
        UUID requesterId,
        String title,
        TicketStatus status,
        TicketPriority priority,
        Instant submittedAt
) {
    public static final String TYPE = "TicketCreatedEvent";
    public static final int VERSION = 1;
}

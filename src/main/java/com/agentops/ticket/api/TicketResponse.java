package com.agentops.ticket.api;

import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String ticketNo,
        UUID requesterId,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        UUID categoryId,
        UUID teamId,
        UUID assigneeId,
        int reopenCount,
        Instant firstResponseAt,
        Instant resolvedAt,
        Instant closedAt,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}

package com.agentops.ticket.application;

import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;

import java.util.UUID;

public record TicketSearchCriteria(
        TicketStatus status,
        TicketPriority priority,
        UUID teamId,
        UUID assigneeId,
        String keyword
) {
}

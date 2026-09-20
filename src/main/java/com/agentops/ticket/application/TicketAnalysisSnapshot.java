package com.agentops.ticket.application;

import java.util.UUID;

/** Read-only application contract; never expose a Ticket entity to the Agent module. */
public record TicketAnalysisSnapshot(
        UUID ticketId, String tenantId, int inputRevision, String title, String description
) {
}

package com.agentops.ticket.api;

import com.agentops.ticket.domain.AssignmentType;

import java.time.Instant;
import java.util.UUID;

public record TicketAssignmentResponse(
        UUID id,
        AssignmentType assignmentType,
        UUID fromTeamId,
        UUID fromAssigneeId,
        UUID toTeamId,
        UUID toAssigneeId,
        String operatorId,
        String reason,
        long ticketVersion,
        Instant occurredAt
) {
}

package com.agentops.ticket.api;

import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.domain.TicketAssignmentRecord;
import com.agentops.ticket.domain.TicketTransitionRecord;

public final class TicketApiMapper {

    private TicketApiMapper() {
    }

    public static TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(), ticket.getTicketNo(), ticket.getRequesterId(), ticket.getTitle(),
                ticket.getDescription(), ticket.getStatus(), ticket.getPriority(), ticket.getCategoryId(),
                ticket.getTeamId(), ticket.getAssigneeId(), ticket.getReopenCount(), ticket.getFirstResponseAt(),
                ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getSubmittedAt(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), ticket.getVersion()
        );
    }

    public static TicketAssignmentResponse toResponse(TicketAssignmentRecord record) {
        return new TicketAssignmentResponse(
                record.getId(), record.getAssignmentType(), record.getFromTeamId(), record.getFromAssigneeId(),
                record.getToTeamId(), record.getToAssigneeId(), record.getOperatorId(), record.getReason(),
                record.getTicketVersion(), record.getOccurredAt()
        );
    }

    public static TicketTransitionResponse toResponse(TicketTransitionRecord record) {
        return new TicketTransitionResponse(
                record.getId(), record.getFromStatus(), record.getToStatus(), record.getActorType(),
                record.getActorId(), record.getReason(), record.getCommandId(), record.getOccurredAt()
        );
    }
}

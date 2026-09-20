package com.agentops.ticket.application;

import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import com.agentops.identity.security.AgentOpsPrincipal;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JpaTicketAnalysisReader implements TicketAnalysisReader {
    private final TicketRepository tickets;
    private final TicketAccessPolicy accessPolicy;

    public JpaTicketAnalysisReader(TicketRepository tickets, TicketAccessPolicy accessPolicy) {
        this.tickets = tickets;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public TicketAnalysisSnapshot read(UUID ticketId, String tenantId) {
        return snapshot(tickets.findByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND)));
    }

    @Override
    public TicketAnalysisSnapshot lockForRunCreation(UUID ticketId, String tenantId) {
        return snapshot(tickets.lockByIdAndTenantId(ticketId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND)));
    }

    @Override
    public TicketAnalysisSnapshot readVisible(UUID ticketId, AgentOpsPrincipal principal) {
        Ticket ticket = tickets.findByIdAndTenantId(ticketId, principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        if (!accessPolicy.canView(ticket, principal)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return snapshot(ticket);
    }

    private TicketAnalysisSnapshot snapshot(Ticket ticket) {
        return new TicketAnalysisSnapshot(ticket.getId(), ticket.getTenantId(),
                ticket.getContentRevision(), ticket.getTitle(), ticket.getDescription());
    }
}

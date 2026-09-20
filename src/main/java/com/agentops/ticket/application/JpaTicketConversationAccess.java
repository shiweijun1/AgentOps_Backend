package com.agentops.ticket.application;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.domain.Ticket;
import com.agentops.ticket.infrastructure.persistence.TicketRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class JpaTicketConversationAccess implements TicketConversationAccess {
    private final TicketRepository tickets;
    private final TicketAccessPolicy policy;
    public JpaTicketConversationAccess(TicketRepository tickets, TicketAccessPolicy policy) {
        this.tickets = tickets; this.policy = policy;
    }
    @Override public Ticket visible(UUID ticketId, AgentOpsPrincipal principal) {
        return requireVisible(tickets.findByIdAndTenantId(ticketId, principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND)), principal);
    }
    @Override public Ticket lockVisible(UUID ticketId, AgentOpsPrincipal principal) {
        return requireVisible(tickets.lockByIdAndTenantId(ticketId, principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND)), principal);
    }
    private Ticket requireVisible(Ticket ticket, AgentOpsPrincipal principal) {
        if (!policy.canView(ticket, principal)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return ticket;
    }
}

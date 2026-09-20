package com.agentops.ticket.application;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.ticket.domain.Ticket;
import java.util.UUID;

/** Application boundary used by the conversation module; no cross-module repository access. */
public interface TicketConversationAccess {
    Ticket visible(UUID ticketId, AgentOpsPrincipal principal);
    Ticket lockVisible(UUID ticketId, AgentOpsPrincipal principal);
}

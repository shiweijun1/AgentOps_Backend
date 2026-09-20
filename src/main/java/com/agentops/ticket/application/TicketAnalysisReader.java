package com.agentops.ticket.application;

import java.util.UUID;
import com.agentops.identity.security.AgentOpsPrincipal;

public interface TicketAnalysisReader {
    TicketAnalysisSnapshot read(UUID ticketId, String tenantId);
    TicketAnalysisSnapshot lockForRunCreation(UUID ticketId, String tenantId);
    TicketAnalysisSnapshot readVisible(UUID ticketId, AgentOpsPrincipal principal);
}

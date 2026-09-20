package com.agentops.ticket.application;

import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.ticket.domain.Ticket;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class TicketAccessPolicy {

    public boolean canView(Ticket ticket, AgentOpsPrincipal principal) {
        if (isAdministrator(principal) || principal.permissions().contains("ticket:read:any")) {
            return true;
        }
        if (principal.permissions().contains("ticket:read:self")
                && ticket.getRequesterId().equals(principal.userId())) {
            return true;
        }
        return principal.permissions().contains("ticket:read:team")
                && (Objects.equals(ticket.getAssigneeId(), principal.userId())
                || principal.teamId() != null && Objects.equals(ticket.getTeamId(), principal.teamId()));
    }

    public boolean isAdministrator(AgentOpsPrincipal principal) {
        return principal.roles().contains("ADMIN");
    }
}

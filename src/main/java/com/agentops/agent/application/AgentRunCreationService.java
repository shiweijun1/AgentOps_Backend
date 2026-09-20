package com.agentops.agent.application;

import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.AgentRunRepository;
import com.agentops.ticket.application.TicketAnalysisReader;
import com.agentops.ticket.application.TicketAnalysisSnapshot;
import com.agentops.ticket.event.TicketCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

@Service
public class AgentRunCreationService {
    private final AgentRunRepository runs;
    private final TicketAnalysisReader tickets;
    private final Clock clock;

    public AgentRunCreationService(AgentRunRepository runs, TicketAnalysisReader tickets, Clock clock) {
        this.runs = runs; this.tickets = tickets; this.clock = clock;
    }

    @Transactional
    public AgentRun fromTicketCreated(TicketCreatedEvent event) {
        TicketAnalysisSnapshot snapshot = tickets.lockForRunCreation(event.aggregateId(), event.tenantId());
        return runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
                snapshot.ticketId(), AgentRunType.TICKET_ANALYSIS, snapshot.inputRevision(), 1)
                .orElseGet(() -> runs.saveAndFlush(AgentRun.pending(snapshot.ticketId(), snapshot.tenantId(),
                        snapshot.inputRevision(), 1, null, AgentTriggerType.TICKET_CREATED, clock.instant())));
    }

    @Transactional
    public AgentRun manual(UUID ticketId, String tenantId) {
        TicketAnalysisSnapshot snapshot = tickets.lockForRunCreation(ticketId, tenantId);
        int previous = runs.maxAttempt(ticketId, AgentRunType.TICKET_ANALYSIS, snapshot.inputRevision());
        UUID parent = previous == 0 ? null : runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(
                ticketId, AgentRunType.TICKET_ANALYSIS, snapshot.inputRevision(), previous)
                .map(AgentRun::getId).orElse(null);
        return runs.saveAndFlush(AgentRun.pending(ticketId, tenantId, snapshot.inputRevision(),
                previous + 1, parent, AgentTriggerType.MANUAL, clock.instant()));
    }
}

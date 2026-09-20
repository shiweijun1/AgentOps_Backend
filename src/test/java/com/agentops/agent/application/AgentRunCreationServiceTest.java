package com.agentops.agent.application;

import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.AgentRunRepository;
import com.agentops.ticket.application.TicketAnalysisReader;
import com.agentops.ticket.application.TicketAnalysisSnapshot;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;
import com.agentops.ticket.event.TicketCreatedEvent;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentRunCreationServiceTest {
    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final TicketAnalysisReader tickets = mock(TicketAnalysisReader.class);
    private final AgentRunCreationService service = new AgentRunCreationService(runs, tickets,
            Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC));
    private final UUID ticketId = UUID.randomUUID();

    @Test
    void eventCreatesExactlyOnePendingRunAndDuplicateReturnsIt() {
        when(tickets.lockForRunCreation(ticketId, "default"))
                .thenReturn(new TicketAnalysisSnapshot(ticketId, "default", 1, "title", "description"));
        when(runs.saveAndFlush(any(AgentRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TicketCreatedEvent event = new TicketCreatedEvent(UUID.randomUUID().toString(), TicketCreatedEvent.TYPE, 1,
                Instant.now(), "trace", "default", ticketId, "T-1", UUID.randomUUID(), "title",
                TicketStatus.NEW, TicketPriority.MEDIUM, Instant.now());
        AgentRun first = service.fromTicketCreated(event);
        assertThat(first.getStatus()).isEqualTo(AgentRunStatus.PENDING);
        assertThat(first.getAttemptNo()).isEqualTo(1);
        when(runs.findByTicketIdAndRunTypeAndInputRevisionAndAttemptNo(ticketId,
                AgentRunType.TICKET_ANALYSIS, 1, 1)).thenReturn(Optional.of(first));
        assertThat(service.fromTicketCreated(event).getId()).isEqualTo(first.getId());
        verify(runs, times(1)).saveAndFlush(any(AgentRun.class));
    }

    @Test
    void manualRerunAdvancesAttemptNumber() {
        when(tickets.lockForRunCreation(ticketId, "default"))
                .thenReturn(new TicketAnalysisSnapshot(ticketId, "default", 1, "title", "description"));
        when(runs.maxAttempt(ticketId, AgentRunType.TICKET_ANALYSIS, 1)).thenReturn(1);
        when(runs.saveAndFlush(any(AgentRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentRun run = service.manual(ticketId, "default");
        assertThat(run.getAttemptNo()).isEqualTo(2);
        assertThat(run.getTriggerType()).isEqualTo(AgentTriggerType.MANUAL);
    }
}

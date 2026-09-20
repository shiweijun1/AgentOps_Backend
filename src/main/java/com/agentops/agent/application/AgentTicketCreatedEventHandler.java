package com.agentops.agent.application;

import com.agentops.ticket.application.TicketCreatedEventHandler;
import com.agentops.ticket.event.TicketCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentTicketCreatedEventHandler implements TicketCreatedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(AgentTicketCreatedEventHandler.class);
    private final AgentRunCreationService creation;

    public AgentTicketCreatedEventHandler(AgentRunCreationService creation) { this.creation = creation; }

    @Override
    public void handle(TicketCreatedEvent event) {
        var run = creation.fromTicketCreated(event);
        log.info("Agent run registered: eventId={}, eventType={}, aggregateId={}, traceId={}, runId={}",
                event.eventId(), event.eventType(), event.aggregateId(), event.traceId(), run.getId());
    }
}

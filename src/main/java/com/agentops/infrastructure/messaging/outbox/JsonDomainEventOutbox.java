package com.agentops.infrastructure.messaging.outbox;

import com.agentops.ticket.application.DomainEventOutbox;
import com.agentops.ticket.event.TicketCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonDomainEventOutbox implements DomainEventOutbox {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public JsonDomainEventOutbox(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(TicketCreatedEvent event) {
        try {
            repository.save(OutboxEvent.pending(
                    event.eventId(), event.aggregateId().toString(), event.eventType(),
                    objectMapper.writeValueAsString(event), event.occurredAt()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize domain event", exception);
        }
    }
}

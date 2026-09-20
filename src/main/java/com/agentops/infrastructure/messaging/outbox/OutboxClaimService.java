package com.agentops.infrastructure.messaging.outbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;
    private final MessagingProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxClaimService(OutboxEventRepository repository, MessagingProperties properties,
                              ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public List<OutboxDispatch> claim(String workerId) {
        Instant now = clock.instant();
        List<OutboxEvent> events = repository.lockClaimable(
                now, properties.getOutbox().getMaxRetries(), properties.getOutbox().getBatchSize()
        );
        Instant lockedUntil = now.plus(properties.getOutbox().getLeaseDuration());
        events.forEach(event -> event.claim(workerId, lockedUntil));
        repository.flush();
        return events.stream().map(this::toDispatch).toList();
    }

    private OutboxDispatch toDispatch(OutboxEvent event) {
        try {
            String traceId = objectMapper.readTree(event.getPayload()).path("traceId").asText(null);
            return new OutboxDispatch(
                    event.getId(), event.getEventId(), event.getEventType(), event.getAggregateId(),
                    traceId, event.getPayload()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored Outbox payload is invalid JSON", exception);
        }
    }
}

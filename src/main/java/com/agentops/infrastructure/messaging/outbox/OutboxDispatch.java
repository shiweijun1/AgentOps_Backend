package com.agentops.infrastructure.messaging.outbox;

import java.util.UUID;

public record OutboxDispatch(
        UUID id,
        String eventId,
        String eventType,
        String aggregateId,
        String traceId,
        String payload
) {
}

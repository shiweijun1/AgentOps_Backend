package com.agentops.infrastructure.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "agentops.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final String workerId = "agentops-" + UUID.randomUUID();
    private final OutboxClaimService claimService;
    private final OutboxResultService resultService;
    private final EventBroker eventBroker;

    public OutboxPublisher(OutboxClaimService claimService, OutboxResultService resultService,
                           EventBroker eventBroker) {
        this.claimService = claimService;
        this.resultService = resultService;
        this.eventBroker = eventBroker;
    }

    public void publishBatch() {
        for (OutboxDispatch event : claimService.claim(workerId)) {
            try {
                eventBroker.publish(event);
                resultService.published(event.id(), workerId);
                log.info(
                        "Outbox event published: eventId={}, eventType={}, aggregateId={}, traceId={}",
                        event.eventId(), event.eventType(), event.aggregateId(), event.traceId()
                );
            } catch (Exception exception) {
                resultService.failed(event.id(), workerId, exception);
                log.warn(
                        "Outbox publish failed: eventId={}, eventType={}, aggregateId={}, traceId={}, errorType={}",
                        event.eventId(), event.eventType(), event.aggregateId(), event.traceId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}

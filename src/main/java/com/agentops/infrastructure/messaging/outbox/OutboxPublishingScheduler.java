package com.agentops.infrastructure.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${agentops.messaging.enabled:true} and ${agentops.messaging.outbox.scheduling-enabled:true}")
public class OutboxPublishingScheduler {

    private final OutboxPublisher publisher;

    public OutboxPublishingScheduler(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${agentops.messaging.outbox.publish-interval:1s}")
    public void publish() {
        publisher.publishBatch();
    }
}

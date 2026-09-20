package com.agentops.infrastructure.messaging.outbox;

public interface EventBroker {

    void publish(OutboxDispatch event);
}

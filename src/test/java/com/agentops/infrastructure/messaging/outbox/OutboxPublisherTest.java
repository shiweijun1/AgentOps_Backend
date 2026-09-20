package com.agentops.infrastructure.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock OutboxClaimService claimService;
    @Mock OutboxResultService resultService;
    @Mock EventBroker eventBroker;

    @Test
    void successfulPublishShouldBeMarkedPublished() {
        OutboxDispatch event = event();
        when(claimService.claim(anyString())).thenReturn(List.of(event));

        new OutboxPublisher(claimService, resultService, eventBroker).publishBatch();

        verify(eventBroker).publish(event);
        verify(resultService).published(org.mockito.ArgumentMatchers.eq(event.id()), anyString());
    }

    @Test
    void failedPublishShouldBeRecordedForRetry() {
        OutboxDispatch event = event();
        when(claimService.claim(anyString())).thenReturn(List.of(event));
        RuntimeException failure = new RuntimeException("broker unavailable");
        doThrow(failure).when(eventBroker).publish(event);

        new OutboxPublisher(claimService, resultService, eventBroker).publishBatch();

        verify(resultService).failed(
                org.mockito.ArgumentMatchers.eq(event.id()), anyString(),
                org.mockito.ArgumentMatchers.same(failure)
        );
    }

    private OutboxDispatch event() {
        return new OutboxDispatch(
                UUID.randomUUID(), UUID.randomUUID().toString(), "TicketCreatedEvent",
                UUID.randomUUID().toString(), "trace-1", "{}"
        );
    }
}

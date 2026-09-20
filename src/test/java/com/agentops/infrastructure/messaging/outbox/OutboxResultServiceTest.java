package com.agentops.infrastructure.messaging.outbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxResultServiceTest {

    @Mock OutboxEventRepository repository;

    private MessagingProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        properties = new MessagingProperties();
        clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void publisherConfirmShouldMarkEventPublished() {
        OutboxEvent event = processingEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        new OutboxResultService(repository, properties, clock).published(event.getId(), "worker-1");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void publishFailureShouldIncreaseRetryCountAndScheduleRetry() {
        OutboxEvent event = processingEvent();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        new OutboxResultService(repository, properties, clock)
                .failed(event.getId(), "worker-1", new RuntimeException("unavailable"));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isAfter(clock.instant());
    }

    private OutboxEvent processingEvent() {
        OutboxEvent event = OutboxEvent.pending(
                "event-1", "ticket-1", "TicketCreatedEvent", "{}", clock.instant()
        );
        event.claim("worker-1", clock.instant().plusSeconds(30));
        return event;
    }
}

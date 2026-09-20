package com.agentops.infrastructure.messaging.inbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import com.agentops.ticket.application.TicketCreatedEventHandler;
import com.agentops.ticket.domain.TicketPriority;
import com.agentops.ticket.domain.TicketStatus;
import com.agentops.ticket.event.TicketCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxProcessingServiceTest {

    @Mock InboxMessageRepository repository;
    @Mock TicketCreatedEventHandler handler;

    private InboxProcessingService service;

    @BeforeEach
    void setUp() {
        service = new InboxProcessingService(
                repository,
                handler,
                new MessagingProperties(),
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void successfulConsumptionShouldMarkInboxProcessed() {
        TicketCreatedEvent event = event();
        when(repository.reserve(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(repository.markProcessed(anyString(), anyString(), any())).thenReturn(1);

        var result = service.process(event, "{}");

        assertThat(result).isEqualTo(InboxProcessingService.ProcessingResult.PROCESSED);
        verify(handler).handle(event);
        verify(repository).markProcessed(event.eventId(), "ticket-created-consumer-v1", Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void processedDuplicateShouldNotInvokeBusinessHandlerAgain() {
        TicketCreatedEvent event = event();
        InboxMessage existing = org.mockito.Mockito.mock(InboxMessage.class);
        when(repository.reserve(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(0);
        when(repository.findByEventIdAndConsumerName(event.eventId(), "ticket-created-consumer-v1"))
                .thenReturn(Optional.of(existing));
        when(existing.getStatus()).thenReturn(InboxStatus.PROCESSED);

        var result = service.process(event, "{}");

        assertThat(result).isEqualTo(InboxProcessingService.ProcessingResult.DUPLICATE);
        verify(handler, never()).handle(any());
        verify(repository, never()).markProcessed(anyString(), anyString(), any());
    }

    @Test
    void handlerFailureShouldNotMarkInboxProcessed() {
        TicketCreatedEvent event = event();
        when(repository.reserve(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("failed")).when(handler).handle(event);

        assertThatThrownBy(() -> service.process(event, "{}"))
                .isInstanceOf(RuntimeException.class);

        verify(repository, never()).markProcessed(anyString(), anyString(), any());
    }

    private TicketCreatedEvent event() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        UUID ticketId = UUID.randomUUID();
        return new TicketCreatedEvent(
                UUID.randomUUID().toString(), TicketCreatedEvent.TYPE, 1, now, "trace-1", "default",
                ticketId, "T20300101-ABC", UUID.randomUUID(), "Title", TicketStatus.NEW,
                TicketPriority.MEDIUM, now
        );
    }
}

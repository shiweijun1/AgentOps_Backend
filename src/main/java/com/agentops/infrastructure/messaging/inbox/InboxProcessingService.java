package com.agentops.infrastructure.messaging.inbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import com.agentops.ticket.application.TicketCreatedEventHandler;
import com.agentops.ticket.event.TicketCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class InboxProcessingService {

    private final InboxMessageRepository repository;
    private final TicketCreatedEventHandler handler;
    private final MessagingProperties properties;
    private final Clock clock;

    public InboxProcessingService(InboxMessageRepository repository, TicketCreatedEventHandler handler,
                                  MessagingProperties properties, Clock clock) {
        this.repository = repository;
        this.handler = handler;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProcessingResult process(TicketCreatedEvent event, String payload) {
        String consumerName = properties.getConsumerName();
        int reserved = repository.reserve(
                UUID.randomUUID().toString(), event.eventId(), consumerName, event.eventType(),
                payload, clock.instant()
        );
        if (reserved == 0) {
            InboxMessage existing = repository.findByEventIdAndConsumerName(event.eventId(), consumerName)
                    .orElseThrow(() -> new RetryableEventProcessingException("Inbox reservation is not visible"));
            if (existing.getStatus() == InboxStatus.PROCESSED) {
                return ProcessingResult.DUPLICATE;
            }
            if (existing.getStatus() == InboxStatus.FAILED) {
                int claimed = repository.claimFailed(event.eventId(), consumerName);
                if (claimed == 0) {
                    InboxMessage refreshed = repository
                            .findByEventIdAndConsumerName(event.eventId(), consumerName)
                            .orElseThrow(() -> new RetryableEventProcessingException("Inbox claim disappeared"));
                    if (refreshed.getStatus() == InboxStatus.PROCESSED) {
                        return ProcessingResult.DUPLICATE;
                    }
                    throw new RetryableEventProcessingException("Inbox message is being processed");
                }
            } else {
                throw new RetryableEventProcessingException("Inbox message is being processed");
            }
        }

        handler.handle(event);
        int updated = repository.markProcessed(event.eventId(), consumerName, clock.instant());
        if (updated != 1) {
            throw new RetryableEventProcessingException("Inbox message could not be marked as processed");
        }
        return ProcessingResult.PROCESSED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recordFailure(TicketCreatedEvent event, String payload, Throwable failure) {
        String consumerName = properties.getConsumerName();
        repository.recordFailure(
                UUID.randomUUID().toString(), event.eventId(), consumerName, event.eventType(), payload,
                safeError(failure), clock.instant()
        );
        return repository.findByEventIdAndConsumerName(event.eventId(), consumerName)
                .map(InboxMessage::getRetryCount)
                .orElse(1);
    }

    private String safeError(Throwable failure) {
        String value = failure.getClass().getSimpleName();
        return value.substring(0, Math.min(value.length(), 1000));
    }

    public enum ProcessingResult {
        PROCESSED,
        DUPLICATE
    }
}

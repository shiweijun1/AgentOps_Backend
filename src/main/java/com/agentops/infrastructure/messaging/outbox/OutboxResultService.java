package com.agentops.infrastructure.messaging.outbox;

import com.agentops.infrastructure.messaging.MessagingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxResultService {

    private final OutboxEventRepository repository;
    private final MessagingProperties properties;
    private final Clock clock;

    public OutboxResultService(OutboxEventRepository repository, MessagingProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void published(UUID id, String workerId) {
        repository.findById(id).ifPresent(event -> event.markPublished(workerId, clock.instant()));
    }

    @Transactional
    public void failed(UUID id, String workerId, Throwable failure) {
        repository.findById(id).ifPresent(event -> {
            Duration delay = retryDelay(event.getRetryCount());
            event.markFailed(
                    workerId,
                    safeError(failure),
                    clock.instant().plus(delay),
                    properties.getOutbox().getMaxRetries()
            );
        });
    }

    private Duration retryDelay(int previousFailures) {
        long multiplier = 1L << Math.min(previousFailures, 10);
        Duration candidate = properties.getOutbox().getRetryBaseDelay().multipliedBy(multiplier);
        Duration maximum = properties.getOutbox().getRetryMaxDelay();
        return candidate.compareTo(maximum) > 0 ? maximum : candidate;
    }

    private String safeError(Throwable failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return value.substring(0, Math.min(value.length(), 1000));
    }
}

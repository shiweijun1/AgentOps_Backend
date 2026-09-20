package com.agentops.infrastructure.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    Optional<OutboxEvent> findByEventId(String eventId);

    Optional<OutboxEvent> findByAggregateId(String aggregateId);

    @Query(value = """
            SELECT *
              FROM outbox_event
             WHERE retry_count < :maxRetries
               AND (
                    status = 'PENDING'
                    OR (status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now)
                    OR (status = 'PROCESSING' AND locked_until IS NOT NULL AND locked_until <= :now)
               )
             ORDER BY created_at
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockClaimable(
            @Param("now") Instant now,
            @Param("maxRetries") int maxRetries,
            @Param("batchSize") int batchSize
    );
}

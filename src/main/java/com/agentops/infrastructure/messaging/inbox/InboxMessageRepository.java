package com.agentops.infrastructure.messaging.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, UUID> {

    Optional<InboxMessage> findByEventIdAndConsumerName(String eventId, String consumerName);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO inbox_message (
                id, event_id, consumer_name, message_type, payload, status,
                retry_count, received_at
            ) VALUES (
                UUID_TO_BIN(:id), :eventId, :consumerName, :messageType, :payload,
                'RECEIVED', 0, :receivedAt
            )
            """, nativeQuery = true)
    int reserve(@Param("id") String id,
                @Param("eventId") String eventId,
                @Param("consumerName") String consumerName,
                @Param("messageType") String messageType,
                @Param("payload") String payload,
                @Param("receivedAt") Instant receivedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inbox_message
               SET status = 'RECEIVED', error_message = NULL
             WHERE event_id = :eventId AND consumer_name = :consumerName AND status = 'FAILED'
            """, nativeQuery = true)
    int claimFailed(@Param("eventId") String eventId, @Param("consumerName") String consumerName);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inbox_message
               SET status = 'PROCESSED', processed_at = :processedAt, error_message = NULL
             WHERE event_id = :eventId AND consumer_name = :consumerName AND status = 'RECEIVED'
            """, nativeQuery = true)
    int markProcessed(@Param("eventId") String eventId,
                      @Param("consumerName") String consumerName,
                      @Param("processedAt") Instant processedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO inbox_message (
                id, event_id, consumer_name, message_type, payload, status,
                retry_count, error_message, received_at
            ) VALUES (
                UUID_TO_BIN(:id), :eventId, :consumerName, :messageType, :payload,
                'FAILED', 1, :errorMessage, :receivedAt
            )
            ON DUPLICATE KEY UPDATE
                status = IF(status = 'PROCESSED', 'PROCESSED', 'FAILED'),
                retry_count = IF(status = 'PROCESSED', retry_count, retry_count + 1),
                error_message = IF(status = 'PROCESSED', error_message, :errorMessage)
            """, nativeQuery = true)
    int recordFailure(@Param("id") String id,
                      @Param("eventId") String eventId,
                      @Param("consumerName") String consumerName,
                      @Param("messageType") String messageType,
                      @Param("payload") String payload,
                      @Param("errorMessage") String errorMessage,
                      @Param("receivedAt") Instant receivedAt);
}

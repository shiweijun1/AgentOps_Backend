package com.agentops.infrastructure.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_message")
public class InboxMessage {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;
    @Column(name = "consumer_name", nullable = false, length = 128)
    private String consumerName;
    @Column(name = "message_type", nullable = false, length = 128)
    private String messageType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "JSON")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InboxStatus status;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected InboxMessage() {
    }

    public InboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
}

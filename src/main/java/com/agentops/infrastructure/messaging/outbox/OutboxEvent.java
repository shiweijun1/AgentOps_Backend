package com.agentops.infrastructure.messaging.outbox;

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
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OutboxStatus status;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "locked_by", length = 128)
    private String lockedBy;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(String eventId, String aggregateId, String eventType,
                                      String payload, Instant createdAt) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.eventId = eventId;
        event.aggregateType = "TICKET";
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = createdAt;
        return event;
    }

    public void claim(String workerId, Instant lockedUntil) {
        status = OutboxStatus.PROCESSING;
        lockedBy = workerId;
        this.lockedUntil = lockedUntil;
    }

    public void markPublished(String workerId, Instant publishedAt) {
        requireOwner(workerId);
        status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        nextRetryAt = null;
        lockedBy = null;
        lockedUntil = null;
        lastError = null;
    }

    public void markFailed(String workerId, String error, Instant nextRetryAt, int maxRetries) {
        requireOwner(workerId);
        retryCount++;
        status = OutboxStatus.FAILED;
        this.nextRetryAt = retryCount < maxRetries ? nextRetryAt : null;
        lastError = error;
        lockedBy = null;
        lockedUntil = null;
    }

    private void requireOwner(String workerId) {
        if (status != OutboxStatus.PROCESSING || !workerId.equals(lockedBy)) {
            throw new IllegalStateException("Outbox event is no longer owned by this publisher");
        }
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getLockedBy() { return lockedBy; }
}

package com.agentops.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;
    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;
    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IdempotencyStatus status;
    @Column(name = "resource_type", length = 64)
    private String resourceType;
    @Column(name = "resource_id", length = 64)
    private String resourceId;
    @Column(name = "response_status")
    private Integer responseStatus;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecord() {
    }

    public String getRequestHash() { return requestHash; }
    public IdempotencyStatus getStatus() { return status; }
    public String getResourceId() { return resourceId; }
}

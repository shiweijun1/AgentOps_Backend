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
@Table(name = "ticket_transition_record")
public class TicketTransitionRecord {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;
    @Column(name = "ticket_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID ticketId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private TicketStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private TicketStatus toStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private TicketActorType actorType;
    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;
    @Column(name = "reason", length = 500)
    private String reason;
    @Column(name = "command_id", nullable = false, length = 128)
    private String commandId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TicketTransitionRecord() {
    }

    public TicketTransitionRecord(UUID id, String tenantId, UUID ticketId, TicketStatus fromStatus,
                                  TicketStatus toStatus, TicketActorType actorType, String actorId,
                                  String reason, String commandId, Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.ticketId = ticketId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.commandId = commandId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public TicketStatus getFromStatus() { return fromStatus; }
    public TicketStatus getToStatus() { return toStatus; }
    public TicketActorType getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getReason() { return reason; }
    public String getCommandId() { return commandId; }
    public Instant getOccurredAt() { return occurredAt; }
}

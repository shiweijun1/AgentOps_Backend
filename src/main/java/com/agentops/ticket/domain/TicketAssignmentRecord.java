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
@Table(name = "ticket_assignment_record")
public class TicketAssignmentRecord {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;
    @Column(name = "ticket_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID ticketId;
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false, length = 32)
    private AssignmentType assignmentType;
    @Column(name = "from_team_id", columnDefinition = "BINARY(16)")
    private UUID fromTeamId;
    @Column(name = "from_assignee_id", columnDefinition = "BINARY(16)")
    private UUID fromAssigneeId;
    @Column(name = "to_team_id", columnDefinition = "BINARY(16)")
    private UUID toTeamId;
    @Column(name = "to_assignee_id", columnDefinition = "BINARY(16)")
    private UUID toAssigneeId;
    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @Column(name = "ticket_version", nullable = false)
    private long ticketVersion;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TicketAssignmentRecord() {
    }

    public TicketAssignmentRecord(UUID id, String tenantId, UUID ticketId, AssignmentType assignmentType,
                                  UUID fromTeamId, UUID fromAssigneeId, UUID toTeamId, UUID toAssigneeId,
                                  String operatorId, String reason, long ticketVersion, Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.ticketId = ticketId;
        this.assignmentType = assignmentType;
        this.fromTeamId = fromTeamId;
        this.fromAssigneeId = fromAssigneeId;
        this.toTeamId = toTeamId;
        this.toAssigneeId = toAssigneeId;
        this.operatorId = operatorId;
        this.reason = reason;
        this.ticketVersion = ticketVersion;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public AssignmentType getAssignmentType() { return assignmentType; }
    public UUID getFromTeamId() { return fromTeamId; }
    public UUID getFromAssigneeId() { return fromAssigneeId; }
    public UUID getToTeamId() { return toTeamId; }
    public UUID getToAssigneeId() { return toAssigneeId; }
    public String getOperatorId() { return operatorId; }
    public String getReason() { return reason; }
    public long getTicketVersion() { return ticketVersion; }
    public Instant getOccurredAt() { return occurredAt; }
}

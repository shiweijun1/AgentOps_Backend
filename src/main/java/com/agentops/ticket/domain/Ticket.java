package com.agentops.ticket.domain;

import com.agentops.shared.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ticket")
public class Ticket extends BaseAuditableEntity {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.NEW, EnumSet.of(TicketStatus.PENDING),
            TicketStatus.PENDING, EnumSet.of(TicketStatus.PROCESSING),
            TicketStatus.PROCESSING, EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED),
            TicketStatus.WAITING_CUSTOMER, EnumSet.of(TicketStatus.PROCESSING),
            TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED, TicketStatus.PROCESSING),
            TicketStatus.CLOSED, EnumSet.of(TicketStatus.PROCESSING)
    );

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "ticket_no", nullable = false, length = 32)
    private String ticketNo;

    @Column(name = "requester_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID requesterId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TicketStatus status;

    @Column(name = "category_id", columnDefinition = "BINARY(16)")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private TicketPriority priority;

    @Column(name = "sentiment", length = 32)
    private String sentiment;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_type", nullable = false, length = 32)
    private RoutingType routingType;

    @Column(name = "manual_reason", length = 64)
    private String manualReason;

    @Column(name = "team_id", columnDefinition = "BINARY(16)")
    private UUID teamId;

    @Column(name = "assignee_id", columnDefinition = "BINARY(16)")
    private UUID assigneeId;

    @Column(name = "content_revision", nullable = false)
    private int contentRevision;

    @Column(name = "content_fingerprint", nullable = false, columnDefinition = "CHAR(64)")
    private String contentFingerprint;

    @Column(name = "possible_duplicate_of", columnDefinition = "BINARY(16)")
    private UUID possibleDuplicateOf;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Ticket() {
    }

    public static Ticket create(
            UUID id,
            String tenantId,
            String ticketNo,
            UUID requesterId,
            String title,
            String description,
            TicketPriority priority,
            String contentFingerprint,
            Instant submittedAt
    ) {
        Ticket ticket = new Ticket();
        ticket.id = id;
        ticket.tenantId = tenantId;
        ticket.ticketNo = ticketNo;
        ticket.requesterId = requesterId;
        ticket.title = title;
        ticket.description = description;
        ticket.status = TicketStatus.NEW;
        ticket.priority = priority;
        ticket.routingType = RoutingType.MANUAL;
        ticket.contentRevision = 1;
        ticket.contentFingerprint = contentFingerprint;
        ticket.reopenCount = 0;
        ticket.submittedAt = submittedAt;
        return ticket;
    }

    public AssignmentSnapshot assign(UUID newTeamId, UUID newAssigneeId) {
        AssignmentSnapshot before = new AssignmentSnapshot(teamId, assigneeId);
        this.teamId = newTeamId;
        this.assigneeId = newAssigneeId;
        return before;
    }

    public TicketStatus transitionTo(TicketStatus target, boolean administrator, Instant occurredAt) {
        TicketStatus previous = status;
        if (!ALLOWED_TRANSITIONS.getOrDefault(previous, Set.of()).contains(target)) {
            throw new IllegalTicketTransitionException(previous, target);
        }
        if (previous == TicketStatus.CLOSED && target == TicketStatus.PROCESSING && !administrator) {
            throw new IllegalTicketTransitionException(previous, target);
        }

        status = target;
        if (target == TicketStatus.RESOLVED) {
            resolvedAt = occurredAt;
        } else if (target == TicketStatus.CLOSED) {
            closedAt = occurredAt;
        } else if (target == TicketStatus.PROCESSING
                && (previous == TicketStatus.RESOLVED || previous == TicketStatus.CLOSED)) {
            reopenCount++;
            resolvedAt = null;
            closedAt = null;
        }
        return previous;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTicketNo() { return ticketNo; }
    public UUID getRequesterId() { return requesterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getContentRevision() { return contentRevision; }
    public TicketStatus getStatus() { return status; }
    public UUID getCategoryId() { return categoryId; }
    public TicketPriority getPriority() { return priority; }
    public UUID getTeamId() { return teamId; }
    public UUID getAssigneeId() { return assigneeId; }
    public int getReopenCount() { return reopenCount; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public long getVersion() { return version; }

    public record AssignmentSnapshot(UUID teamId, UUID assigneeId) {
    }
}

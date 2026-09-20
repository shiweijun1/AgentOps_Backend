package com.agentops.agent.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_suggestion")
public class AiSuggestion {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "ticket_id", nullable = false, columnDefinition = "BINARY(16)") private UUID ticketId;
    @Column(name = "run_id", nullable = false, columnDefinition = "BINARY(16)") private UUID runId;
    @Column(name = "input_revision", nullable = false) private int inputRevision;
    @Column(name = "original_content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String originalContent;
    @Column(name = "edited_content", columnDefinition = "TEXT") private String editedContent;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32)
    private AiSuggestionStatus status;
    @Column(name = "confidence", nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;
    @Column(name = "superseded_by", columnDefinition = "BINARY(16)") private UUID supersededBy;
    @Column(name = "adopted_by", columnDefinition = "BINARY(16)") private UUID adoptedBy;
    @Column(name = "adopted_at") private Instant adoptedAt;
    @Column(name = "edited_by", length = 64) private String editedBy;
    @Column(name = "edited_at") private Instant editedAt;
    @Column(name = "rejected_by", length = 64) private String rejectedBy;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @Column(name = "final_content_snapshot", columnDefinition = "TEXT") private String finalContentSnapshot;
    @Column(name = "source_message_id", columnDefinition = "BINARY(16)") private UUID sourceMessageId;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AiSuggestion() {}

    public static AiSuggestion ready(AgentRun run, String content, BigDecimal confidence,
                                     RiskLevel riskLevel, Instant now) {
        AiSuggestion value = new AiSuggestion();
        value.id = UUID.randomUUID(); value.tenantId = run.getTenantId(); value.ticketId = run.getTicketId();
        value.runId = run.getId(); value.inputRevision = run.getInputRevision();
        value.originalContent = content; value.status = AiSuggestionStatus.READY;
        value.confidence = confidence; value.riskLevel = riskLevel; value.createdAt = now;
        return value;
    }

    public void edit(String content, String actor, Instant now) {
        requireReviewable();
        editedContent = content; editedBy = actor; editedAt = now; status = AiSuggestionStatus.EDITED;
    }

    public void adopt(UUID actor, Instant now) {
        requireReviewable();
        adoptedBy = actor; adoptedAt = now;
        finalContentSnapshot = editedContent == null ? originalContent : editedContent;
        status = AiSuggestionStatus.ADOPTED;
    }

    public void reject(String actor, String reason, Instant now) {
        requireReviewable();
        rejectedBy = actor; rejectedAt = now; rejectionReason = reason;
        status = AiSuggestionStatus.REJECTED;
    }

    public void supersede(UUID newSuggestionId) {
        requireReviewable();
        supersededBy = newSuggestionId; status = AiSuggestionStatus.SUPERSEDED;
    }

    private void requireReviewable() {
        if (status != AiSuggestionStatus.READY && status != AiSuggestionStatus.EDITED)
            throw new IllegalStateException("Suggestion is no longer reviewable");
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getTicketId() { return ticketId; }
    public UUID getRunId() { return runId; }
    public int getInputRevision() { return inputRevision; }
    public String getOriginalContent() { return originalContent; }
    public String getEditedContent() { return editedContent; }
    public AiSuggestionStatus getStatus() { return status; }
    public BigDecimal getConfidence() { return confidence; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public UUID getSupersededBy() { return supersededBy; }
    public UUID getAdoptedBy() { return adoptedBy; }
    public Instant getAdoptedAt() { return adoptedAt; }
    public String getEditedBy() { return editedBy; }
    public Instant getEditedAt() { return editedAt; }
    public String getRejectedBy() { return rejectedBy; }
    public Instant getRejectedAt() { return rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getFinalContentSnapshot() { return finalContentSnapshot; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}

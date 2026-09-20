package com.agentops.agent.domain;

import com.agentops.ticket.domain.TicketPriority;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_analysis_result")
public class AiAnalysisResult {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "ticket_id", nullable = false, columnDefinition = "BINARY(16)") private UUID ticketId;
    @Column(name = "run_id", nullable = false, columnDefinition = "BINARY(16)") private UUID runId;
    @Column(name = "input_revision", nullable = false) private int inputRevision;
    @Enumerated(EnumType.STRING) @Column(name = "category_code", nullable = false, length = 32) private CategoryCode categoryCode;
    @Column(name = "suggested_category_id", columnDefinition = "BINARY(16)") private UUID suggestedCategoryId;
    @Enumerated(EnumType.STRING) @Column(name = "suggested_priority", length = 16) private TicketPriority suggestedPriority;
    @Enumerated(EnumType.STRING) @Column(name = "sentiment", length = 32) private Sentiment sentiment;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 16) private RiskLevel riskLevel;
    @Column(name = "confidence", nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @Column(name = "reason", length = 1000) private String reason;
    @Column(name = "manual_required", nullable = false) private boolean manualRequired;
    @Column(name = "manual_reason", length = 64) private String manualReason;
    @Column(name = "applied_at") private Instant appliedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AiAnalysisResult() {}

    public AiAnalysisResult(AgentRun run, CategoryCode categoryCode, TicketPriority priority,
                            Sentiment sentiment, RiskLevel riskLevel, BigDecimal confidence,
                            boolean manualRequired, String manualReason, String reason, Instant now) {
        id = UUID.randomUUID(); tenantId = run.getTenantId(); ticketId = run.getTicketId();
        runId = run.getId(); inputRevision = run.getInputRevision(); this.categoryCode = categoryCode;
        suggestedPriority = priority; this.sentiment = sentiment; this.riskLevel = riskLevel;
        this.confidence = confidence; this.manualRequired = manualRequired;
        this.manualReason = manualReason; this.reason = reason; createdAt = now;
    }

    public UUID getRunId() { return runId; }
    public UUID getTicketId() { return ticketId; }
    public CategoryCode getCategoryCode() { return categoryCode; }
    public TicketPriority getSuggestedPriority() { return suggestedPriority; }
    public Sentiment getSentiment() { return sentiment; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isManualRequired() { return manualRequired; }
    public String getManualReason() { return manualReason; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}

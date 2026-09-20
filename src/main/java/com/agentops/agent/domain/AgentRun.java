package com.agentops.agent.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_run")
public class AgentRun {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "ticket_id", nullable = false, columnDefinition = "BINARY(16)") private UUID ticketId;
    @Enumerated(EnumType.STRING) @Column(name = "run_type", nullable = false, length = 32) private AgentRunType runType;
    @Column(name = "input_revision", nullable = false) private int inputRevision;
    @Column(name = "attempt_no", nullable = false) private int attemptNo;
    @Column(name = "execution_count", nullable = false) private int executionCount;
    @Column(name = "parent_run_id", columnDefinition = "BINARY(16)") private UUID parentRunId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private AgentRunStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 32) private AgentTriggerType triggerType;
    @Column(name = "model_provider", length = 64) private String modelProvider;
    @Column(name = "model_name", length = 128) private String modelName;
    @Column(name = "prompt_version", length = 64) private String promptVersion;
    @Column(name = "worker_id", length = 128) private String workerId;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "input_tokens", nullable = false) private int inputTokens;
    @Column(name = "output_tokens", nullable = false) private int outputTokens;
    @Column(name = "estimated_cost", nullable = false, precision = 18, scale = 8) private BigDecimal estimatedCost;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Version @Column(name = "version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AgentRun() {}

    public static AgentRun pending(UUID ticketId, String tenantId, int revision, int attempt,
                                   UUID parentRunId, AgentTriggerType trigger, Instant now) {
        AgentRun run = new AgentRun();
        run.id = UUID.randomUUID(); run.ticketId = ticketId; run.tenantId = tenantId;
        run.runType = AgentRunType.TICKET_ANALYSIS; run.inputRevision = revision;
        run.attemptNo = attempt; run.parentRunId = parentRunId;
        run.executionCount = 0;
        run.status = AgentRunStatus.PENDING; run.triggerType = trigger;
        run.inputTokens = 0; run.outputTokens = 0; run.estimatedCost = BigDecimal.ZERO;
        run.createdAt = now; run.updatedAt = now;
        return run;
    }

    public void succeed(String owner, Instant now, String provider, String model, int inputTokens, int outputTokens) {
        requireOwner(owner, now);
        status = AgentRunStatus.SUCCEEDED; workerId = null; leaseUntil = null;
        modelProvider = provider; modelName = model; promptVersion = "ticket-analysis-v1";
        this.inputTokens = inputTokens; this.outputTokens = outputTokens;
        errorCode = null; errorMessage = null; finishedAt = now; updatedAt = now;
    }

    public void fail(String owner, Instant now, String code, String message) {
        requireOwner(owner, now);
        status = AgentRunStatus.FAILED; workerId = null; leaseUntil = null;
        errorCode = code; errorMessage = message; finishedAt = now; updatedAt = now;
    }

    public void requireOwner(String owner, Instant now) {
        if (status != AgentRunStatus.RUNNING || !owner.equals(workerId)
                || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalStateException("Agent run lease lost");
        }
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public String getTenantId() { return tenantId; }
    public AgentRunType getRunType() { return runType; }
    public int getInputRevision() { return inputRevision; }
    public int getAttemptNo() { return attemptNo; }
    public int getExecutionCount() { return executionCount; }
    public UUID getParentRunId() { return parentRunId; }
    public AgentRunStatus getStatus() { return status; }
    public AgentTriggerType getTriggerType() { return triggerType; }
    public String getModelProvider() { return modelProvider; }
    public String getModelName() { return modelName; }
    public String getWorkerId() { return workerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.agentops.agent.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_step")
public class AgentStep {
    @Id @Column(name = "id", columnDefinition = "BINARY(16)") private UUID id;
    @Column(name = "run_id", nullable = false, columnDefinition = "BINARY(16)") private UUID runId;
    @Enumerated(EnumType.STRING) @Column(name = "step_type", nullable = false, length = 64) private AgentStepType stepType;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private AgentStepStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "input_snapshot", columnDefinition = "JSON") private String inputSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "output_snapshot", columnDefinition = "JSON") private String outputSnapshot;
    @Column(name = "model_name", length = 128) private String modelName;
    @Column(name = "prompt_version", length = 64) private String promptVersion;
    @Column(name = "input_tokens", nullable = false) private int inputTokens;
    @Column(name = "output_tokens", nullable = false) private int outputTokens;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;

    protected AgentStep() {}

    public AgentStep(UUID runId, AgentStepType type, int sequence, AgentStepStatus status,
                     String inputSummary, String outputSummary, String modelName,
                     int inputTokens, int outputTokens, int retryCount, String errorCode,
                     Instant started, Instant finished) {
        this(runId, type, sequence, status, inputSummary, outputSummary, modelName,
                modelName == null ? null : "ticket-analysis-v1", inputTokens, outputTokens,
                retryCount, errorCode, started, finished);
    }

    public AgentStep(UUID runId, AgentStepType type, int sequence, AgentStepStatus status,
                     String inputSummary, String outputSummary, String modelName, String promptVersion,
                     int inputTokens, int outputTokens, int retryCount, String errorCode,
                     Instant started, Instant finished) {
        this.id = UUID.randomUUID(); this.runId = runId; this.stepType = type;
        this.sequenceNo = sequence; this.status = status;
        this.inputSnapshot = inputSummary; this.outputSnapshot = outputSummary;
        this.modelName = modelName; this.promptVersion = promptVersion;
        this.inputTokens = inputTokens; this.outputTokens = outputTokens;
        this.retryCount = retryCount; this.errorCode = errorCode;
        this.errorMessage = errorCode; this.startedAt = started; this.finishedAt = finished;
        this.durationMs = Math.max(0, java.time.Duration.between(started, finished).toMillis());
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public AgentStepType getStepType() { return stepType; }
    public int getSequenceNo() { return sequenceNo; }
    public AgentStepStatus getStatus() { return status; }
    public String getInputSnapshot() { return inputSnapshot; }
    public String getOutputSnapshot() { return outputSnapshot; }
    public String getModelName() { return modelName; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public Long getDurationMs() { return durationMs; }
    public int getRetryCount() { return retryCount; }
    public String getErrorCode() { return errorCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}

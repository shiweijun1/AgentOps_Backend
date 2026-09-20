package com.agentops.agent.api;

import com.agentops.agent.domain.*;
import java.time.Instant;
import java.util.UUID;

public record AgentRunResponse(UUID id, UUID ticketId, AgentRunType runType, int inputRevision,
                               int attemptNo, int executionCount, AgentTriggerType triggerType, AgentRunStatus status,
                               String modelProvider, String modelName, int inputTokens, int outputTokens,
                               String errorCode, Instant startedAt, Instant finishedAt, Instant createdAt) {
    public static AgentRunResponse from(AgentRun run) {
        return new AgentRunResponse(run.getId(), run.getTicketId(), run.getRunType(), run.getInputRevision(),
                run.getAttemptNo(), run.getExecutionCount(), run.getTriggerType(), run.getStatus(), run.getModelProvider(),
                run.getModelName(), run.getInputTokens(), run.getOutputTokens(), run.getErrorCode(),
                run.getStartedAt(), run.getFinishedAt(), run.getCreatedAt());
    }
}

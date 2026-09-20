package com.agentops.agent.api;

import com.agentops.agent.domain.*;
import java.time.Instant;
import java.util.UUID;

public record AgentStepResponse(UUID id, AgentStepType stepType, int sequenceNo, AgentStepStatus status,
                                String inputSummary, String outputSummary, String modelName,
                                int inputTokens, int outputTokens, Long durationMs, int retryCount,
                                String errorCode, Instant startedAt, Instant finishedAt) {
    public static AgentStepResponse from(AgentStep step) {
        return new AgentStepResponse(step.getId(), step.getStepType(), step.getSequenceNo(), step.getStatus(),
                step.getInputSnapshot(), step.getOutputSnapshot(), step.getModelName(), step.getInputTokens(),
                step.getOutputTokens(), step.getDurationMs(), step.getRetryCount(), step.getErrorCode(),
                step.getStartedAt(), step.getFinishedAt());
    }
}

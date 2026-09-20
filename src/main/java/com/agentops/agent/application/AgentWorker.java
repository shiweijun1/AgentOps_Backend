package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.agentops.agent.domain.*;
import com.agentops.ticket.application.TicketAnalysisReader;
import com.agentops.ticket.application.TicketAnalysisSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class AgentWorker {
    private static final Logger log = LoggerFactory.getLogger(AgentWorker.class);
    private final AgentRunClaimService claims;
    private final AgentRunCompletionService completion;
    private final TicketAnalysisReader tickets;
    private final TicketAnalysisModel model;
    private final AnalysisOutputValidator validator;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentWorker(AgentRunClaimService claims, AgentRunCompletionService completion,
                       TicketAnalysisReader tickets, TicketAnalysisModel model,
                       AnalysisOutputValidator validator, AgentProperties properties,
                       ObjectMapper mapper, Clock clock) {
        this.claims = claims; this.completion = completion; this.tickets = tickets;
        this.model = model; this.validator = validator; this.properties = properties;
        this.mapper = mapper; this.clock = clock;
        if (properties.getLeaseDuration().compareTo(properties.getModelTimeout().plusSeconds(5)) <= 0) {
            throw new IllegalArgumentException("Agent lease must exceed model timeout by at least 5 seconds");
        }
        if (properties.getLowConfidenceThreshold() < 0 || properties.getLowConfidenceThreshold() > 1) {
            throw new IllegalArgumentException("Agent confidence threshold must be between 0 and 1");
        }
    }

    public void runBatch() {
        for (int index = 0; index < properties.getBatchSize(); index++) {
            var claim = claims.claimOne();
            if (claim.isEmpty()) return;
            execute(claim.get());
        }
    }

    private void execute(AgentRunClaimService.Claim claim) {
        List<AgentStep> history = new ArrayList<>();
        AgentStepType current = AgentStepType.CONTENT_PREPROCESSING;
        Instant started = clock.instant();
        try {
            TicketAnalysisSnapshot snapshot = tickets.read(claim.ticketId(), claim.tenantId());
            String title = normalize(snapshot.title(), 200);
            String description = normalize(snapshot.description(), 8000);
            if (title.isBlank() || description.isBlank()) throw new IllegalArgumentException("Empty ticket content");
            history.add(step(claim, current, 1, AgentStepStatus.SUCCEEDED,
                    summary(Map.of("titleLength", snapshot.title().length(), "descriptionLength", snapshot.description().length())),
                    summary(Map.of("titleLength", title.length(), "descriptionLength", description.length())),
                    null, 0, 0, started, clock.instant(), null));

            current = AgentStepType.TICKET_CLASSIFICATION; started = clock.instant();
            Future<TicketAnalysisModel.ModelResponse> future = executor.submit(
                    () -> model.analyze(new TicketAnalysisModel.ModelRequest(title, description)));
            TicketAnalysisModel.ModelResponse response;
            try {
                response = future.get(properties.getModelTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw exception;
            } catch (InterruptedException exception) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw exception;
            }
            if (response.inputTokens() < 0 || response.outputTokens() < 0) {
                throw new IllegalArgumentException("Invalid token counts");
            }
            AnalysisOutput output = validator.validate(response.json());
            history.add(step(claim, current, 2, AgentStepStatus.SUCCEEDED,
                    summary(Map.of("inputRevision", snapshot.inputRevision())),
                    summary(Map.of("categoryCode", output.categoryCode().name(),
                            "priority", output.priority().name(), "sentiment", output.sentiment().name())),
                    response.modelName(), response.inputTokens(), response.outputTokens(), started, clock.instant(), null));

            current = AgentStepType.RISK_EVALUATION; started = clock.instant();
            history.add(step(claim, current, 3, AgentStepStatus.SUCCEEDED,
                    summary(Map.of("confidence", output.confidence(), "riskLevel", output.riskLevel().name())),
                    summary(Map.of("manualRequired", output.manualRequired())), null, 0, 0,
                    started, clock.instant(), null));

            current = AgentStepType.RESULT_PERSISTENCE; started = clock.instant();
            history.add(step(claim, current, 4, AgentStepStatus.SUCCEEDED,
                    summary(Map.of("runId", claim.runId().toString())),
                    summary(Map.of("result", "persisted")), null, 0, 0, started, clock.instant(), null));
            completion.succeed(claim, history, output, model, response);
            log.info("Agent run succeeded: runId={}, ticketId={}, provider={}", claim.runId(), claim.ticketId(), model.provider());
        } catch (Exception exception) {
            String code = errorCode(exception);
            history.add(step(claim, current, history.size() + 1, AgentStepStatus.FAILED,
                    summary(Map.of("stage", current.name())), summary(Map.of("errorCode", code)),
                    null, 0, 0, started, clock.instant(), code));
            try {
                completion.fail(claim, history, code);
            } catch (IllegalStateException | org.springframework.orm.ObjectOptimisticLockingFailureException leaseLost) {
                log.warn("Agent result discarded after lease loss: runId={}, ticketId={}", claim.runId(), claim.ticketId());
                return;
            }
            log.warn("Agent run failed: runId={}, ticketId={}, errorCode={}", claim.runId(), claim.ticketId(), code);
        }
    }

    private AgentStep step(AgentRunClaimService.Claim claim, AgentStepType type, int sequence,
                           AgentStepStatus status, String input, String output, String modelName,
                           int inputTokens, int outputTokens, Instant started, Instant finished, String error) {
        return new AgentStep(claim.runId(), type, sequence, status, input, output, modelName,
                inputTokens, outputTokens, Math.max(0, claim.executionCount() - 1), error, started, finished);
    }

    private String summary(Map<String, ?> data) {
        try { return mapper.writeValueAsString(data); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize summary", exception); }
    }

    private String normalize(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").strip();
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }

    private String errorCode(Throwable error) {
        if (error instanceof TimeoutException) return "MODEL_TIMEOUT";
        if (error instanceof InvalidAnalysisOutputException) return "INVALID_MODEL_JSON";
        if (error instanceof ExecutionException cause && cause.getCause() instanceof java.net.http.HttpTimeoutException)
            return "MODEL_TIMEOUT";
        return "AGENT_EXECUTION_FAILED";
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }
}

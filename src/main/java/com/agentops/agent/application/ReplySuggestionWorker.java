package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.AiAnalysisResultRepository;
import com.agentops.agent.infrastructure.persistence.AgentRunRepository;
import com.agentops.knowledge.application.KnowledgeApplicationService;
import com.agentops.knowledge.application.KnowledgeSearchHit;
import com.agentops.ticket.application.TicketAnalysisReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
public class ReplySuggestionWorker {
    private static final Logger log = LoggerFactory.getLogger(ReplySuggestionWorker.class);
    private final AgentRunClaimService claims;
    private final ReplySuggestionCompletionService completion;
    private final AiAnalysisResultRepository analyses;
    private final AgentRunRepository runs;
    private final TicketAnalysisReader tickets;
    private final KnowledgeApplicationService knowledge;
    private final ReplyDraftModel model;
    private final ReplyDraftValidator validator;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ReplySuggestionWorker(AgentRunClaimService claims, ReplySuggestionCompletionService completion,
            AiAnalysisResultRepository analyses, AgentRunRepository runs,
            TicketAnalysisReader tickets, KnowledgeApplicationService knowledge,
            ReplyDraftModel model, ReplyDraftValidator validator, AgentProperties properties,
            ObjectMapper mapper, Clock clock) {
        this.claims = claims; this.completion = completion; this.analyses = analyses; this.runs = runs; this.tickets = tickets;
        this.knowledge = knowledge; this.model = model; this.validator = validator;
        this.properties = properties; this.mapper = mapper; this.clock = clock;
    }

    public void runBatch() {
        for (int i = 0; i < properties.getBatchSize(); i++) {
            Optional<AgentRunClaimService.Claim> claim = claims.claimOne(AgentRunType.REPLY_SUGGESTION);
            if (claim.isEmpty()) return;
            execute(claim.get());
        }
    }

    private void execute(AgentRunClaimService.Claim claim) {
        List<AgentStep> history = new ArrayList<>();
        AgentStepType stage = AgentStepType.KNOWLEDGE_RETRIEVAL;
        Instant started = clock.instant();
        try {
            AgentRun run = runs.findById(claim.runId()).orElseThrow();
            AiAnalysisResult analysis = analyses.findByRunId(run.getParentRunId()).orElseThrow();
            if (!analysis.getTicketId().equals(claim.ticketId())) throw new ManualHandling("INVALID_PARENT_ANALYSIS");
            var snapshot = tickets.read(claim.ticketId(), claim.tenantId());
            if (snapshot.inputRevision() != run.getInputRevision()) throw new ManualHandling("STALE_TICKET");
            if (analysis.isManualRequired() || analysis.getRiskLevel() == RiskLevel.HIGH
                    || analysis.getConfidence().doubleValue() < properties.getLowConfidenceThreshold())
                throw new ManualHandling("ANALYSIS_REQUIRES_HUMAN");
            String keyword = snapshot.title().strip();
            if (keyword.codePointCount(0, keyword.length()) > 100)
                keyword = keyword.substring(0, keyword.offsetByCodePoints(0, 100));
            List<KnowledgeSearchHit> hits = retrieve(claim.tenantId(), keyword);
            if (hits.isEmpty())
                throw new ManualHandling("NO_RELIABLE_KNOWLEDGE");
            history.add(step(claim, stage, history.size() + 1, started, clock.instant(),
                    Map.of("ticketId", claim.ticketId()), Map.of("hitCount", hits.size()), null, 0, 0));

            stage = AgentStepType.REPLY_GENERATION; started = clock.instant();
            Future<ReplyDraftModel.ModelResponse> future = executor.submit(() -> model.draft(
                    new ReplyDraftModel.ModelRequest(snapshot.title(), snapshot.description(), hits)));
            ReplyDraftModel.ModelResponse response;
            try { response = future.get(properties.getModelTimeout().toMillis(), TimeUnit.MILLISECONDS); }
            catch (TimeoutException | InterruptedException exception) {
                future.cancel(true);
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                throw exception;
            }
            if (response.inputTokens() < 0 || response.outputTokens() < 0) throw new ManualHandling("INVALID_TOKEN_USAGE");
            ReplyDraft draft = validator.validate(response.json(), hits);
            if (draft.confidence() < properties.getLowConfidenceThreshold())
                throw new ManualHandling("LOW_REPLY_CONFIDENCE");
            history.add(step(claim, stage, history.size() + 1, started, clock.instant(),
                    Map.of("sourceCount", hits.size()), Map.of("citationCount", draft.citationChunkIds().size()),
                    response.modelName(), response.inputTokens(), response.outputTokens()));
            stage = AgentStepType.CITATION_VALIDATION; started = clock.instant();
            history.add(step(claim, stage, history.size() + 1, started, clock.instant(),
                    Map.of("retrievedCount", hits.size()), Map.of("validatedCount", draft.citationChunkIds().size()),
                    null, 0, 0));
            stage = AgentStepType.SUGGESTION_PERSISTENCE; started = clock.instant();
            history.add(step(claim, stage, history.size() + 1, started, clock.instant(),
                    Map.of("runId", claim.runId()), Map.of("result", "persisted"), null, 0, 0));
            completion.succeed(claim, history, hits, draft, analysis.getRiskLevel(), model, response);
            log.info("Reply suggestion succeeded: runId={}, ticketId={}", claim.runId(), claim.ticketId());
        } catch (Exception exception) {
            String code = errorCode(exception);
            history.add(step(claim, stage, history.size() + 1, started, clock.instant(),
                    Map.of("stage", stage.name()), Map.of("errorCode", code), null, 0, 0, code));
            try { completion.fail(claim, history, code); }
            catch (IllegalStateException | org.springframework.orm.ObjectOptimisticLockingFailureException lost) {
                log.warn("Reply run result discarded after lease loss: runId={}", claim.runId());
                return;
            }
            log.warn("Reply suggestion requires human handling: runId={}, ticketId={}, errorCode={}",
                    claim.runId(), claim.ticketId(), code);
        }
    }

    private AgentStep step(AgentRunClaimService.Claim claim, AgentStepType type, int sequence,
            Instant started, Instant finished, Map<String, ?> input, Map<String, ?> output,
            String modelName, int inputTokens, int outputTokens) {
        return step(claim, type, sequence, started, finished, input, output, modelName, inputTokens, outputTokens, null);
    }
    private AgentStep step(AgentRunClaimService.Claim claim, AgentStepType type, int sequence,
            Instant started, Instant finished, Map<String, ?> input, Map<String, ?> output,
            String modelName, int inputTokens, int outputTokens, String errorCode) {
        try {
            return new AgentStep(claim.runId(), type, sequence,
                    errorCode == null ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                    mapper.writeValueAsString(input), mapper.writeValueAsString(output), modelName,
                    modelName == null ? null : "reply-suggestion-v1",
                    inputTokens, outputTokens, Math.max(0, claim.executionCount() - 1), errorCode, started, finished);
        } catch (Exception exception) { throw new IllegalStateException("Cannot serialize step summary", exception); }
    }
    private String errorCode(Exception error) {
        if (error instanceof ManualHandling manual) return manual.code;
        if (error instanceof TimeoutException || error instanceof ExecutionException execution
                && execution.getCause() instanceof java.net.http.HttpTimeoutException) return "MODEL_TIMEOUT";
        if (error instanceof InvalidAnalysisOutputException) return "UNVERIFIABLE_MODEL_OUTPUT";
        return "REPLY_GENERATION_FAILED";
    }
    private boolean relevant(String query, String content) {
        int[] points = query.toLowerCase(java.util.Locale.ROOT).codePoints().toArray();
        String source = content.toLowerCase(java.util.Locale.ROOT);
        if (points.length < 2) return false;
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i < points.length - 1; i++)
            if (!Character.isWhitespace(points[i]) && !Character.isWhitespace(points[i + 1]))
                pairs.add(new String(points, i, 2));
        if (pairs.isEmpty()) return false;
        long matched = pairs.stream().filter(source::contains).count();
        return matched >= Math.max(1, (pairs.size() * 4 + 4) / 5);
    }
    private List<KnowledgeSearchHit> retrieve(String tenantId, String query) {
        Map<UUID, KnowledgeSearchHit> candidates = new LinkedHashMap<>();
        for (KnowledgeSearchHit hit : knowledge.search(tenantId, query, 10))
            candidates.putIfAbsent(hit.chunkId(), hit);
        int[] points = query.codePoints().toArray();
        Set<String> pairs = new LinkedHashSet<>();
        for (int i = 0; i < points.length - 1 && pairs.size() < 8; i++) {
            if (!Character.isWhitespace(points[i]) && !Character.isWhitespace(points[i + 1]))
                pairs.add(new String(points, i, 2));
        }
        for (String pair : pairs)
            for (KnowledgeSearchHit hit : knowledge.search(tenantId, pair, 5))
                candidates.putIfAbsent(hit.chunkId(), hit);
        return candidates.values().stream().filter(hit -> hit.score() > 0 && relevant(query, hit.snippet()))
                .limit(3).toList();
    }
    private static final class ManualHandling extends RuntimeException {
        private final String code;
        ManualHandling(String code) { this.code = code; }
    }
    @PreDestroy public void shutdown() { executor.shutdownNow(); }
}

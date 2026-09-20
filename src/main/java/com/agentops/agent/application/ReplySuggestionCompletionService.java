package com.agentops.agent.application;

import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.*;
import com.agentops.knowledge.application.*;
import com.agentops.ticket.application.TicketAnalysisReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

@Service
public class ReplySuggestionCompletionService {
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final AiSuggestionRepository suggestions;
    private final KnowledgeCitationRepository citations;
    private final KnowledgeCitationVerifier verifier;
    private final TicketAnalysisReader tickets;
    private final Clock clock;

    public ReplySuggestionCompletionService(AgentRunRepository runs, AgentStepRepository steps,
            AiSuggestionRepository suggestions, KnowledgeCitationRepository citations,
            KnowledgeCitationVerifier verifier, TicketAnalysisReader tickets, Clock clock) {
        this.runs = runs; this.steps = steps; this.suggestions = suggestions;
        this.citations = citations; this.verifier = verifier; this.tickets = tickets; this.clock = clock;
    }

    @Transactional
    public void succeed(AgentRunClaimService.Claim claim, List<AgentStep> history,
            List<KnowledgeSearchHit> retrieved, ReplyDraft draft, RiskLevel riskLevel,
            ReplyDraftModel model, ReplyDraftModel.ModelResponse response) {
        AgentRun run = runs.findById(claim.runId()).orElseThrow();
        run.requireOwner(claim.owner(), clock.instant());
        if (run.getRunType() != AgentRunType.REPLY_SUGGESTION
                || tickets.lockForRunCreation(run.getTicketId(), run.getTenantId()).inputRevision() != run.getInputRevision())
            throw new IllegalStateException("Stale ticket revision");
        List<UUID> ids = retrieved.stream().map(KnowledgeSearchHit::chunkId).distinct().toList();
        List<VerifiedKnowledgeChunk> verified = verifier.lockCurrent(run.getTenantId(), ids, clock.instant());
        if (verified.size() != ids.size()) throw new IllegalStateException("Knowledge source no longer current");
        Map<UUID, VerifiedKnowledgeChunk> byId = new HashMap<>();
        verified.forEach(value -> byId.put(value.chunkId(), value));
        for (KnowledgeSearchHit hit : retrieved) {
            VerifiedKnowledgeChunk current = byId.get(hit.chunkId());
            if (current == null || !current.articleId().equals(hit.articleId())
                    || !current.versionId().equals(hit.versionId()) || !current.content().equals(hit.snippet()))
                throw new IllegalStateException("Knowledge source changed");
        }
        if (suggestions.findByRunId(run.getId()).isPresent())
            throw new IllegalStateException("Suggestion already created for run");
        AiSuggestion suggestion = AiSuggestion.ready(run, draft.content(),
                BigDecimal.valueOf(draft.confidence()), riskLevel, clock.instant());
        for (AiSuggestion previous : suggestions.findByTenantIdAndTicketIdAndStatusIn(
                run.getTenantId(), run.getTicketId(), List.of(AiSuggestionStatus.READY, AiSuggestionStatus.EDITED))) {
            previous.supersede(suggestion.getId());
        }
        suggestions.saveAndFlush(suggestion);
        Set<UUID> used = Set.copyOf(draft.citationChunkIds());
        List<KnowledgeCitation> sources = new ArrayList<>();
        for (int index = 0; index < retrieved.size(); index++) {
            KnowledgeSearchHit hit = retrieved.get(index);
            sources.add(new KnowledgeCitation(run.getTenantId(), suggestion.getId(), hit.articleId(),
                    hit.versionId(), hit.chunkId(), hit.score(), index + 1,
                    byId.get(hit.chunkId()).content(), used.contains(hit.chunkId())));
        }
        citations.saveAllAndFlush(sources);
        steps.deleteByRunId(run.getId());
        steps.saveAll(history);
        run.succeed(claim.owner(), clock.instant(), model.provider(), response.modelName(),
                "reply-suggestion-v1", response.inputTokens(), response.outputTokens());
    }

    @Transactional
    public void fail(AgentRunClaimService.Claim claim, List<AgentStep> history, String code) {
        AgentRun run = runs.findById(claim.runId()).orElseThrow();
        run.requireOwner(claim.owner(), clock.instant());
        steps.deleteByRunId(run.getId());
        steps.saveAll(history);
        run.fail(claim.owner(), clock.instant(), code, code);
    }
}

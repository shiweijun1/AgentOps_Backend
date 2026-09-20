package com.agentops.agent.application;

import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Service
public class AgentRunCompletionService {
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final AiAnalysisResultRepository results;
    private final Clock clock;

    public AgentRunCompletionService(AgentRunRepository runs, AgentStepRepository steps,
                                     AiAnalysisResultRepository results, Clock clock) {
        this.runs = runs; this.steps = steps; this.results = results; this.clock = clock;
    }

    @Transactional
    public void succeed(AgentRunClaimService.Claim claim, List<AgentStep> history, AnalysisOutput output,
                        TicketAnalysisModel model, TicketAnalysisModel.ModelResponse response) {
        AgentRun run = runs.findById(claim.runId()).orElseThrow();
        run.requireOwner(claim.owner(), clock.instant());
        steps.deleteByRunId(run.getId());
        steps.saveAll(history);
        results.save(new AiAnalysisResult(run, output.categoryCode(), output.priority(), output.sentiment(),
                output.riskLevel(), BigDecimal.valueOf(output.confidence()), output.manualRequired(),
                output.manualReason(), output.reason(), clock.instant()));
        run.succeed(claim.owner(), clock.instant(), model.provider(), response.modelName(),
                response.inputTokens(), response.outputTokens());
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

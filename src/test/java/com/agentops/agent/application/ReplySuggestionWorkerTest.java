package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.AgentRunRepository;
import com.agentops.agent.infrastructure.persistence.AiAnalysisResultRepository;
import com.agentops.knowledge.application.KnowledgeApplicationService;
import com.agentops.ticket.application.TicketAnalysisReader;
import com.agentops.ticket.application.TicketAnalysisSnapshot;
import com.agentops.ticket.domain.TicketPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;

class ReplySuggestionWorkerTest {
    @Test void highRiskNeverCallsKnowledgeOrModelAndFailsForManualHandling() {
        AgentRunClaimService claims = mock(AgentRunClaimService.class);
        ReplySuggestionCompletionService completion = mock(ReplySuggestionCompletionService.class);
        AiAnalysisResultRepository analyses = mock(AiAnalysisResultRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        TicketAnalysisReader tickets = mock(TicketAnalysisReader.class);
        KnowledgeApplicationService knowledge = mock(KnowledgeApplicationService.class);
        ReplyDraftModel model = mock(ReplyDraftModel.class);
        UUID ticketId = UUID.randomUUID();
        AgentRun parent = AgentRun.pending(ticketId, "tenant-a", 1, 1, null,
                AgentTriggerType.MANUAL, Instant.now());
        AgentRun reply = AgentRun.pending(ticketId, "tenant-a", AgentRunType.REPLY_SUGGESTION,
                1, 1, parent.getId(), AgentTriggerType.ANALYSIS_SUCCEEDED, Instant.now());
        var claim = new AgentRunClaimService.Claim(reply.getId(), ticketId, "tenant-a", "worker", 1);
        AiAnalysisResult result = new AiAnalysisResult(parent, CategoryCode.OTHER, TicketPriority.MEDIUM,
                Sentiment.NEUTRAL, RiskLevel.HIGH, BigDecimal.valueOf(0.9), true,
                "HIGH_RISK", "risk", Instant.now());
        when(claims.claimOne(AgentRunType.REPLY_SUGGESTION)).thenReturn(Optional.of(claim), Optional.empty());
        when(runs.findById(reply.getId())).thenReturn(Optional.of(reply));
        when(analyses.findByRunId(parent.getId())).thenReturn(Optional.of(result));
        when(tickets.read(ticketId, "tenant-a")).thenReturn(new TicketAnalysisSnapshot(
                ticketId, "tenant-a", 1, "账号登录", "登录失败"));
        var worker = new ReplySuggestionWorker(claims, completion, analyses, runs, tickets, knowledge,
                model, new ReplyDraftValidator(new ObjectMapper()), new AgentProperties(),
                new ObjectMapper(), Clock.systemUTC());
        try {
            worker.runBatch();
            verify(completion).fail(eq(claim), anyList(), eq("ANALYSIS_REQUIRES_HUMAN"));
            verifyNoInteractions(knowledge, model);
        } finally { worker.shutdown(); }
    }
}

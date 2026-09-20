package com.agentops.agent.application;

import com.agentops.agent.config.AgentProperties;
import com.agentops.agent.domain.*;
import com.agentops.ticket.application.TicketAnalysisReader;
import com.agentops.ticket.application.TicketAnalysisSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentWorkerTest {
    private final AgentRunClaimService claims = mock(AgentRunClaimService.class);
    private final AgentRunCompletionService completion = mock(AgentRunCompletionService.class);
    private final TicketAnalysisReader tickets = mock(TicketAnalysisReader.class);
    private final TicketAnalysisModel model = mock(TicketAnalysisModel.class);
    private final AgentProperties properties = new AgentProperties();
    private final UUID ticketId = UUID.randomUUID();
    private final AgentRunClaimService.Claim claim = new AgentRunClaimService.Claim(
            UUID.randomUUID(), ticketId, "default", "owner", 1);
    private AgentWorker worker;

    @AfterEach void close() { if (worker != null) worker.shutdown(); }

    private void setup() {
        when(claims.claimOne()).thenReturn(Optional.of(claim), Optional.empty());
        when(tickets.read(ticketId, "default"))
                .thenReturn(new TicketAnalysisSnapshot(ticketId, "default", 1, "Login fails", "My account is locked"));
        ObjectMapper mapper = new ObjectMapper();
        worker = new AgentWorker(claims, completion, tickets, model,
                new AnalysisOutputValidator(mapper, properties), properties, mapper, Clock.systemUTC());
    }

    @Test
    void successfulAnalysisPersistsFourStepsAndResult() throws Exception {
        setup();
        when(model.analyze(any())).thenReturn(response("LOW", 0.9));
        worker.runBatch();
        var steps = org.mockito.ArgumentCaptor.forClass(List.class);
        var output = org.mockito.ArgumentCaptor.forClass(AnalysisOutput.class);
        verify(completion).succeed(eq(claim), steps.capture(), output.capture(), eq(model), any());
        assertThat(steps.getValue()).hasSize(4);
        assertThat(output.getValue().categoryCode()).isEqualTo(CategoryCode.ACCOUNT);
        verify(completion, never()).fail(any(), any(), any());
    }

    @Test
    void invalidJsonFailsRunWithoutSavingResult() throws Exception {
        setup();
        when(model.analyze(any())).thenReturn(new TicketAnalysisModel.ModelResponse("{bad", "test", 0, 0));
        worker.runBatch();
        verify(completion).fail(eq(claim), anyList(), eq("INVALID_MODEL_JSON"));
        verify(completion, never()).succeed(any(), any(), any(), any(), any());
    }

    @Test
    void timeoutFailsRunAndCancelsModelCall() throws Exception {
        properties.setModelTimeout(Duration.ofMillis(50));
        setup();
        CountDownLatch interrupted = new CountDownLatch(1);
        when(model.analyze(any())).thenAnswer(invocation -> {
            try { Thread.sleep(5000); } catch (InterruptedException exception) { interrupted.countDown(); throw exception; }
            return response("LOW", 0.9);
        });
        worker.runBatch();
        verify(completion).fail(eq(claim), anyList(), eq("MODEL_TIMEOUT"));
        assertThat(interrupted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void highRiskForcesManualReview() throws Exception {
        setup();
        when(model.analyze(any())).thenReturn(response("HIGH", 0.9));
        worker.runBatch();
        var output = org.mockito.ArgumentCaptor.forClass(AnalysisOutput.class);
        verify(completion).succeed(eq(claim), anyList(), output.capture(), eq(model), any());
        assertThat(output.getValue().manualRequired()).isTrue();
        assertThat(output.getValue().manualReason()).isEqualTo("HIGH_RISK");
    }

    private TicketAnalysisModel.ModelResponse response(String risk, double confidence) {
        return new TicketAnalysisModel.ModelResponse("""
                {"categoryCode":"ACCOUNT","priority":"MEDIUM","sentiment":"NEGATIVE",
                 "riskLevel":"%s","confidence":%s,"manualRequired":false,
                 "manualReason":null,"reason":"Account issue"}
                """.formatted(risk, confidence), "test-model", 10, 5);
    }
}

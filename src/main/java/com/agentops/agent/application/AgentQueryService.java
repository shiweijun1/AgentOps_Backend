package com.agentops.agent.application;

import com.agentops.agent.domain.*;
import com.agentops.agent.infrastructure.persistence.*;
import com.agentops.identity.security.AgentOpsPrincipal;
import com.agentops.shared.exception.BusinessException;
import com.agentops.shared.exception.ErrorCode;
import com.agentops.ticket.application.TicketAnalysisReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentQueryService {
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final AiAnalysisResultRepository results;
    private final TicketAnalysisReader tickets;
    private final AgentRunCreationService creation;

    public AgentQueryService(AgentRunRepository runs, AgentStepRepository steps,
                             AiAnalysisResultRepository results, TicketAnalysisReader tickets,
                             AgentRunCreationService creation) {
        this.runs = runs; this.steps = steps; this.results = results;
        this.tickets = tickets; this.creation = creation;
    }

    @Transactional(readOnly = true)
    public List<AgentRun> list(UUID ticketId, AgentOpsPrincipal principal) {
        tickets.readVisible(ticketId, principal);
        return runs.findByTenantIdAndTicketIdOrderByCreatedAtDesc(principal.tenantId(), ticketId);
    }

    @Transactional(readOnly = true)
    public AgentRun get(UUID runId, AgentOpsPrincipal principal) {
        AgentRun run = runs.findByIdAndTenantId(runId, principal.tenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        tickets.readVisible(run.getTicketId(), principal);
        return run;
    }

    @Transactional(readOnly = true)
    public List<AgentStep> steps(UUID runId, AgentOpsPrincipal principal) {
        get(runId, principal);
        return steps.findByRunIdOrderBySequenceNo(runId);
    }

    @Transactional(readOnly = true)
    public Optional<AiAnalysisResult> analysis(UUID ticketId, AgentOpsPrincipal principal) {
        tickets.readVisible(ticketId, principal);
        return results.findFirstByTenantIdAndTicketIdOrderByCreatedAtDesc(principal.tenantId(), ticketId);
    }

    public AgentRun rerun(UUID ticketId, AgentOpsPrincipal principal) {
        tickets.readVisible(ticketId, principal);
        return creation.manual(ticketId, principal.tenantId());
    }
}
